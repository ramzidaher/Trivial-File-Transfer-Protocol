package server;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class TFTPTCPSocketServer {
    // Define constants

    private static final int BUFFER_SIZE = 512;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the server port number: ");
        int portNumber = scanner.nextInt();
        System.out.println("  TTTTTTTTTT   CCCCCCCCCC   PPPPPPPPPP  ");
        System.out.println("  TTTTTTTTTT   CCCCCCCCCC   PPPPPPPPPPP ");
        System.out.println("     TTT      CCC          PPP    PPPP ");
        System.out.println("     TTT      CCC          PPP    PPP  ");
        System.out.println("     TTT      CCC          PPPPPPPPP   ");
        System.out.println("     TTT      CCC          PPPPPP     ");
        System.out.println("     TTT      CCC          PPP        ");
        System.out.println("     TTT      CCCCCCCCCC   PPP        ");
        System.out.println("     TTT      CCCCCCCCCC   PPP        ");
        String serverName = "TCP Server";
        System.out.printf("~~~~~~~~~~~  %s  ~~~~~~~~~~~\n", serverName);
        System.out.printf("Server listening on port %d...\n", portNumber);
        System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        ServerSocket serverSocket = new ServerSocket(portNumber);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Connection established with client " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            new Thread(() -> {
                try {
                    handleClient(clientSocket);
                } catch (IOException e) {
                    System.out.println("Error handling client: " + e.getMessage());
                    e.printStackTrace(); // Add this line to

                }
            }).start();
        }
    }
    /**
     * Handles communication with a TFTP client.
     * Performs the TFTP protocol handshake and handles both read and write requests.
     *
     * @param clientSocket the socket connected to the client
     * @throws IOException if there is an error communicating with the client
     */
    private static void handleClient(Socket clientSocket) throws IOException {
        // Create input and output streams for the client socket
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
        // Perform handshake
        sendHandshake(out);
        receiveHandshake(in);
        // Read the TFTP packet from the client
        byte[] packetData = new byte[BUFFER_SIZE + 4];
        int bytesRead = in.read(packetData, 0, packetData.length);
        // If no bytes were read, the packet is invalid
        if (bytesRead == -1) {
            System.out.println("Invalid packet received.");
            return;
        }
        // Determine the opcode of the packet
        byte opcode = packetData[1];
        // Handle the packet based on its opcode
        if (opcode == OP_WRQ) {
            handleWriteRequest(out, in, packetData);
        } else if (opcode == OP_RRQ) {
            handleReadRequest(out, in, packetData);
        } else {
            System.out.println("Invalid opcode received: " + opcode);
        }
        // Close the client socket
        clientSocket.close();
    }

    /**
     * Handles a read request from a TFTP client by reading the requested file from disk
     * and sending it back to the client in data packets.
     * @param out the output stream to send data packets to the client
     * @param in the input stream to receive acknowledgement packets from the client
     * @param packetData the initial read request packet from the client
     * @throws IOException if there is an error reading the file or communicating with the client
     */
    private static void handleReadRequest(DataOutputStream out, DataInputStream in, byte[] packetData) throws IOException {
        // Parse the filename from the read request packet
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        byte[] fileNameBytes = new byte[BUFFER_SIZE];
        int fileNameLength = 0;
        for (int i = 2; i < packetData.length; i++) {
            if (packetData[i] == 0) {
                fileNameLength = i - 2;
                break;
            }
            fileNameBytes[i - 2] = packetData[i];
        }
        // Convert the filename to a string and get its basename
        String fileName = new String(fileNameBytes, 0, fileNameLength);
        fileName = new File("src/Retrieved Files/" + fileName).getName();
        // Read the file from disk and send it to the client in data packets
        try (FileInputStream fis = new FileInputStream("src/Retrieve Files/" + fileName)) {
            short blockNumber = 1;
            byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
            while (true) {
                int bytesRead = fis.read(dataBuffer, 4, BUFFER_SIZE);
                if (bytesRead == -1) {
                    break;
                }
                // Create a data packet and send it to the client
                dataBuffer[0] = 0;
                dataBuffer[1] = OP_DATA;
                dataBuffer[2] = (byte) (blockNumber >> 8);
                dataBuffer[3] = (byte) (blockNumber & 0xFF);
                out.write(dataBuffer, 0, bytesRead + 4);
                out.flush();
                // Increment the block number for the next data packet
                blockNumber++;
                // If we read less than the buffer size, we've reached the end of the file
                if (bytesRead < BUFFER_SIZE) {
                    System.out.println("File transfer to client completed for " + fileName);
                    break;
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + e.getMessage());
        }
    }

    /**
     * Sends a TFTP protocol handshake to the client over the output stream.
     * @param out the output stream to send the handshake to
     * @throws IOException if there is an error sending the handshake
     */
    private static void sendHandshake(DataOutputStream out) throws IOException {
        // Create a byte array containing the handshake string
        byte[] handshake = "HANDSHAKE".getBytes();
        // Send the handshake to the client over the output stream
        out.write(handshake);
    }


    /**
     * Receives a TFTP protocol handshake from the client over the input stream and verifies it.
     *
     * @param in the input stream to receive the handshake from
     * @throws IOException if there is an error receiving the handshake or the handshake is invalid
     */
    private static void receiveHandshake(DataInputStream in) throws IOException {
        // Read the handshake string from the input stream into a buffer
        byte[] handshakeBuffer = new byte[9];
        in.readFully(handshakeBuffer);
        // Convert the handshake buffer to a string
        String handshake = new String(handshakeBuffer);
        // Check if the handshake is valid
        if (!handshake.equals("HANDSHAKE")) {
            throw new IOException("Invalid handshake received: " + handshake);
        }
    }
    /**
     * Handles a write request from a TFTP client by writing the received data to a file on disk.
     *
     * @param out the output stream to send acknowledgement packets to the client
     * @param in the input stream to receive data packets from the client
     * @param packetData the initial write request packet from the client
     * @throws IOException if there is an error writing the file or communicating with the client
     */
    private static void handleWriteRequest(DataOutputStream out, DataInputStream in, byte[] packetData) throws IOException {
        // Parse the filename from the write request packet
        ByteBuffer buffer = ByteBuffer.wrap(packetData);
        byte[] fileNameBytes = new byte[BUFFER_SIZE];
        int fileNameLength = 0;
        for (int i = 2; i < packetData.length; i++) {
            if (packetData[i] == 0) {
                fileNameLength = i - 2;
                break;
            }
            fileNameBytes[i - 2] = packetData[i];
        }
        // Convert the filename to a string and get its basename
        String fileName = new String(fileNameBytes, 0, fileNameLength);
        fileName = new File("src/Received Files/" + fileName).getName();
        // Write the received data to the file on disk
        try (FileOutputStream fos = new FileOutputStream("src/Received Files/" + fileName)) {
            short blockNumber = 1;

            while (true) {
                // Read the next data packet from the client
                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                int bytesRead = in.read(dataBuffer, 0, dataBuffer.length);

                if (bytesRead == -1) {
                    break;
                }
                // Extract the block number from the data packet
                short receivedBlockNumber = (short) (((dataBuffer[2] & 0xFF) << 8) | (dataBuffer[3] & 0xFF));
                // If the block number is correct, write the data to the file
                if (receivedBlockNumber == blockNumber) {
                    int dataSize = bytesRead - 4;
                    fos.write(dataBuffer, 4, dataSize);
                    // Send an acknowledgement packet to the client
                    blockNumber++;
                    // If we received less than the buffer size, we've reached the end of the file
                    if (dataSize < BUFFER_SIZE) {
                        System.out.println("File transfer to server completed for " + fileName);
                        break;
                    }
                } else {
                    // If the block number is incorrect, send an error packet to the client
                    System.out.println("Received data packet with incorrect block number. Expected " + blockNumber + ", but received " + receivedBlockNumber);
                    sendError(out, "Incorrect block number");
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
            sendError(out, "Error writing to file");
        }
    }
    /**
     * Sends an error packet to the client with the specified error message.
     *
     * @param out the output stream to send the error packet to
     * @param errorMessage the error message to include in the error packet
     * @throws IOException if there is an error sending the error packet
     */
    private static void sendError(DataOutputStream out, String errorMessage) throws IOException {
        // Create a byte array for the error packet
        byte[] errorPacket = new byte[4 + errorMessage.length() + 1];
        // Fill in the error packet fields
        errorPacket[0] = 0;
        errorPacket[1] = OP_ERROR;
        errorPacket[2] = 0;
        errorPacket[3] = 5; // Error code 5
        byte[] messageBytes = errorMessage.getBytes();
        System.arraycopy(messageBytes, 0, errorPacket, 4, messageBytes.length);
        errorPacket[errorPacket.length - 1] = 0; // Null terminator
        // Send the error packet to the client over the output stream
        out.write(errorPacket);
    }


}


