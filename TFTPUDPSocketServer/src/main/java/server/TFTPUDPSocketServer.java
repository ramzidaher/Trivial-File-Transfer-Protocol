package server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class TFTPUDPSocketServer {
    private static final int BUFFER_SIZE = 512;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;

    public static void main(String[] args) throws IOException {
        // Create scanner to get user input
        Scanner scanner = new Scanner(System.in);
        // Get port number from user
        System.out.print("Enter the server port number: ");
        int portNumber = scanner.nextInt();

        // Print server banner
        System.out.println("  UUU    UUU   DDDDDDDDDDD    PPPPPPPPPP  ");
        System.out.println("  UUU    UUU   DDDDDDDDDDDD   PPPPPPPPPPP ");
        System.out.println("  UUU    UUU   DDD       DDD  PPP    PPPP ");
        System.out.println("  UUU    UUU   DDD        DDD PPP    PPP  ");
        System.out.println("  UUU    UUU   DDD        DDD PPPPPPPPP   ");
        System.out.println("  UUU    UUU   DDD        DDD PPPPPP     ");
        System.out.println("  UUU    UUU   DDD        DDD PPP        ");
        System.out.println("   UUUUUUUU    DDDDDDDDDDDD  PPP        ");
        System.out.println("   UUUUUUUU    DDDDDDDDDDD   PPP        ");
        String serverName = "UDP Server";
        System.out.printf("~~~~~~~~~~~  %s  ~~~~~~~~~~~\n", serverName);
        System.out.printf("Server listening on port %d...\n", portNumber);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Create server socket
        DatagramSocket serverSocket = new DatagramSocket(portNumber);

        // Start main server loop
        while (true) {
            // Create buffer for incoming data
            byte[] buffer = new byte[BUFFER_SIZE + 4];
            DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
            serverSocket.receive(receivedPacket);
            System.out.println("Connection established with client " + receivedPacket.getAddress() + ":" + receivedPacket.getPort());

            // Extract client information and packet data
            InetAddress clientAddress = receivedPacket.getAddress();
            int clientPort = receivedPacket.getPort();
            byte[] packetData = receivedPacket.getData();

            // Determine packet opcode
            byte opcode = packetData[1];

            // Handle read or write request
            if (opcode == OP_WRQ) {
                handleWriteRequest(serverSocket, packetData, clientAddress, clientPort);
            } else if (opcode == OP_RRQ) {
                handleReadRequest(serverSocket, packetData, clientAddress, clientPort);
            } else {
                System.out.println("Invalid opcode received: " + opcode);
            }
        }
    }

    /**
     * Handles a read request from the client. Sends the requested file to the client.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param packetData    the data received in the read request packet
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @throws IOException if an error occurs while sending or receiving data
     */
    private static void handleReadRequest(DatagramSocket serverSocket, byte[] packetData, InetAddress clientAddress, int clientPort) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        byte[] fileNameBytes = new byte[BUFFER_SIZE];
        int fileNameLength = 0;
        // Extract the file name from the packet data
        for (int i = 2; i < packetData.length; i++) {
            if (packetData[i] == 0) {
                fileNameLength = i - 2;
                break;
            }
            fileNameBytes[i - 2] = packetData[i];
        }
        // Convert the file name bytes to a String and get the file name without the path
        String fileName = new String(fileNameBytes, 0, fileNameLength);
        fileName = new File("src/Retreived Files/" + fileName).getName();
        // Send the file to the client in chunks
        try (FileInputStream fis = new FileInputStream("src/Retrieve Files/" + fileName)) {
            short blockNumber = 1;
            byte[] dataBuffer = new byte[BUFFER_SIZE + 4];

            while (true) {
                // Read a chunk of data from the file
                int bytesRead = fis.read(dataBuffer, 4, BUFFER_SIZE);
                if (bytesRead == -1) {
                    // End of file, stop sending data
                    break;
                }
                // Add the block number and opcode to the data buffer
                dataBuffer[0] = 0;
                dataBuffer[1] = OP_DATA;
                dataBuffer[2] = (byte) (blockNumber >> 8);
                dataBuffer[3] = (byte) (blockNumber & 0xFF);

                // Send the data packet to the client and wait for an ACK packet
                DatagramPacket sendPacket = new DatagramPacket(dataBuffer, bytesRead + 4, clientAddress, clientPort);
                int dataSize = sendPacket.getLength() - 4;
                serverSocket.send(sendPacket);
                receiveAck(serverSocket, clientAddress, clientPort, blockNumber);

                blockNumber++;
                // Check if the last packet was received and break out of the loop
                if (dataSize < BUFFER_SIZE) {
                    System.out.println("File transfer to client completed for " + fileName);
                    break;
                }

                }
        } catch (IOException e) {
            // An error occurred while reading the file
            System.out.println("Error reading from file: " + e.getMessage());
            sendError(serverSocket, clientAddress, clientPort, e.getMessage());
        }
    }

    /**
     * Waits to receive an ACK packet with the expected block number from the client.
     *
     * @param serverSocket        the DatagramSocket used by the server
     * @param clientAddress       the InetAddress of the client
     * @param clientPort          the port number of the client
     * @param expectedBlockNumber the block number expected in the ACK packet
     * @throws IOException if an error occurs while receiving data
     */
    private static void receiveAck(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, short expectedBlockNumber) throws IOException {
        byte[] ackBuffer = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        serverSocket.receive(ackPacket);

        // Check that the received packet is an ACK packet
        if (ackPacket.getData()[1] != OP_ACK) {
            System.out.println("Invalid opcode received: " + ackPacket.getData()[1]);
            return;
        }
        // Check that the received block number is the expected block number
        short receivedBlockNumber = (short) (((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF));
        if (receivedBlockNumber != expectedBlockNumber) {
            System.out.println("Received ACK packet with incorrect block number. Expected " + expectedBlockNumber + ", but received " + receivedBlockNumber);
        }

    }

    /**
     * Handles a write request from the client. Receives a file from the client and saves it to disk.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param packetData    the data received in the write request packet
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @throws IOException if an error occurs while sending or receiving data
     */
    private static void handleWriteRequest(DatagramSocket serverSocket, byte[] packetData, InetAddress clientAddress, int clientPort) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        byte[] fileNameBytes = new byte[BUFFER_SIZE];
        int fileNameLength = 0;

        // Extract the file name from the packet data
        for (int i = 2; i < packetData.length; i++) {
            if (packetData[i] == 0) {
                fileNameLength = i - 2;
                break;
            }
            fileNameBytes[i - 2] = packetData[i];
        }

        // Convert the file name bytes to a String and get the file name without the path
        String fileName = new String(fileNameBytes, 0, fileNameLength);
        fileName = new File(fileName).getName();

        // Send an initial ACK packet to the client
        sendInitialAck(serverSocket, clientAddress, clientPort);

        // Receive data packets from the client and write them to the file
        writeToFile(serverSocket, clientAddress, clientPort, fileName);
    }


    /**
     * Sends an initial ACK packet to the client with block number 0.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @throws IOException if an error occurs while sending data
     */
    private static void sendInitialAck(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] ackPacket = createAckPacket((short) 0);
        DatagramPacket sendPacket = new DatagramPacket(ackPacket, ackPacket.length, clientAddress, clientPort);
        serverSocket.send(sendPacket);
    }

    /**
     * Creates an ACK packet with the specified block number.
     *
     * @param blockNumber the block number to include in the ACK packet
     * @return a byte array containing the ACK packet
     */
    private static byte[] createAckPacket(short blockNumber) {
        byte[] ackPacket = new byte[4];
        ackPacket[0] = 0;
        ackPacket[1] = OP_ACK;
        ackPacket[2] = (byte) (blockNumber >> 8);
        ackPacket[3] = (byte) (blockNumber & 0xFF);

        return ackPacket;
    }

    /**
     * Receives data packets from the client and writes them to the specified file.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @param fileName      the name of the file to write the data to
     * @throws IOException if an error occurs while receiving or writing data
     */
    private static void writeToFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String fileName) throws IOException {
        try (FileOutputStream fos = new FileOutputStream("src/Received Files/" + fileName)) {
            short blockNumber = 1;
            System.out.println("Received block number: " + blockNumber);
            while (true) {
                // Receive a data packet from the client
                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                serverSocket.receive(dataPacket);
                // Check that the data packet is from the correct client and has the expected block number
                if (clientAddress.equals(dataPacket.getAddress()) && clientPort == dataPacket.getPort()) {
                    short receivedBlockNumber = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
                    if (receivedBlockNumber == blockNumber) {
                        // Write the data to the file
                        int dataSize = dataPacket.getLength() - 4;
                        fos.write(dataPacket.getData(), 4, dataSize);

                        // Send an ACK packet with the current block number
                        sendAck(serverSocket, clientAddress, clientPort, blockNumber);
                        blockNumber++;

                        // Check if the last packet was received and break out of the loop
                        if (dataSize < BUFFER_SIZE) {
                            System.out.println("File transfer to server completed for " + fileName);
                            break;
                        }
                    } else {
                        System.out.println("Received data packet with incorrect block number. Expected " + blockNumber + ", but received " + receivedBlockNumber);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    /**
     * Sends an ACK packet with the specified block number to the client.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @param blockNumber   the block number to include in the ACK packet
     * @throws IOException if an error occurs while sending data
     */
    private static void sendAck(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, short blockNumber) throws IOException {
        byte[] ackPacket = createAckPacket(blockNumber);
        DatagramPacket sendPacket = new DatagramPacket(ackPacket, ackPacket.length, clientAddress, clientPort);
        serverSocket.send(sendPacket);
    }

    /**
     * Sends an error packet to the client with the specified error message.
     *
     * @param serverSocket  the DatagramSocket used by the server
     * @param clientAddress the InetAddress of the client
     * @param clientPort    the port number of the client
     * @param errorMessage  the error message to include in the error packet
     * @throws IOException if an error occurs while sending data
     */
    private static void sendError(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String errorMessage) throws IOException {
        byte[] errorPacket = new byte[4 + errorMessage.length() + 1];
        errorPacket[0] = 0;
        errorPacket[1] = OP_ERROR;
        errorPacket[2] = 0;
        errorPacket[3] = 5; // Error code 5
        byte[] messageBytes = errorMessage.getBytes();
        System.arraycopy(messageBytes, 0, errorPacket, 4, messageBytes.length);
        errorPacket[errorPacket.length - 1] = 0; // Null terminator
        DatagramPacket sendPacket = new DatagramPacket(errorPacket, errorPacket.length, clientAddress, clientPort);
        serverSocket.send(sendPacket);
    }
}




