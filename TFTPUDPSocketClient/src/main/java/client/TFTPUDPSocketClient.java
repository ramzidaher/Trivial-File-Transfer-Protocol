package client;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

    public class TFTPUDPSocketClient {
        private static final int BUFFER_SIZE = 512;
        private static final byte OP_RRQ = 1;
        private static final byte OP_WRQ = 2;
        private static final byte OP_DATA = 3;
        private static final byte OP_ACK = 4;
        private static final byte OP_ERROR = 5;
        private static boolean running = true;

        /**
         * The main method creates a TFTP UDP socket client to send or retrieve files to/from a server.
         *
         * @param args the command line arguments
         * @throws IOException if there is an I/O error
         */
        public static void main(String[] args) throws IOException {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the server IP address: ");
            String serverAddress = scanner.nextLine();
            System.out.print("Enter the server port number: ");
            int portNumber = scanner.nextInt();
            scanner.nextLine();
            while (running) {
                System.out.println("**NOTE**");
                System.out.println("If you want to send a file, the file should be in the 'Sending Files' directory. To retrieve a file it should in the servers 'Retrieve Files' directory");
                try (DatagramSocket clientSocket = new DatagramSocket()) {
                    clientSocket.setSoTimeout(5000);

                    System.out.print("Enter the filename: ");
                    String fileName = scanner.next();

                    System.out.print("Press 1 to send the file to the server or 2 to retrieve the file from the server: ");
                    int choice = scanner.nextInt();

                    if (choice == 1) {
                        sendWriteRequest(clientSocket, fileName, InetAddress.getByName(serverAddress), portNumber);
                    } else if (choice == 2) {
                        sendReadRequest(clientSocket, fileName, InetAddress.getByName(serverAddress), portNumber);
                    } else {
                        System.out.println("Invalid choice.");
                        return;
                    }

                } catch (SocketException e) {
                    System.out.println("Socket error: " + e.getMessage());
                } catch (UnknownHostException e) {
                    System.out.println("Unknown host: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("I/O error: " + e.getMessage());
                }
                System.out.println("**NOTE**");

                System.out.println("To send/retrieve only one file hit n and if you want to send/retrieve all the files at once hit n and continue on");
                System.out.print("Do you want to continue? (y/n): ");
                String continueInput = scanner.next();
                if (continueInput.equalsIgnoreCase("n")) {
                    running = false;
                }
            }
        }

        /**
         * Sends a write request to the server containing the specified file name.
         *
         * @param clientSocket  The DatagramSocket object used to send and receive data.
         * @param fileName      The name of the file to send.
         * @param serverAddress The IP address of the server.
         * @param serverPort    The port number to use for communication with the server.
         * @throws IOException If an I/O error occurs while sending the packet.
         */
        private static void sendWriteRequest(DatagramSocket clientSocket, String fileName, InetAddress serverAddress, int serverPort) throws IOException {
            byte[] wrqPacket = createWrqPacket(fileName);
            DatagramPacket sendPacket = new DatagramPacket(wrqPacket, wrqPacket.length, serverAddress, serverPort);
            clientSocket.send(sendPacket);
            receiveInitialAck(clientSocket);
            sendFile(clientSocket, serverAddress, serverPort, fileName);
        }

        /**
         * Sends a read request to the server containing the specified file name.
         *
         * @param clientSocket  The DatagramSocket object used to send and receive data.
         * @param fileName      The name of the file to retrieve.
         * @param serverAddress The IP address of the server.
         * @param serverPort    The port number to use for communication with the server.
         * @throws IOException If an I/O error occurs while sending the packet.
         */
        private static void sendReadRequest(DatagramSocket clientSocket, String fileName, InetAddress serverAddress, int serverPort) throws IOException {
            fileName = new File(fileName).getName(); // removes the path from the file name
            // create a read request packet for the given file
            byte[] rrqPacket = createRrqPacket("src/Retrieved Files/" + fileName);
            // create a DatagramPacket containing the read request packet, the server's IP address, and the server's port number
            DatagramPacket sendPacket = new DatagramPacket(rrqPacket, rrqPacket.length, serverAddress, serverPort);
            // send the DatagramPacket to the server
            clientSocket.send(sendPacket);
            // receive the file from the server
            receiveFile(clientSocket, fileName);
        }

        /**
         * Sends the specified file to the server in chunks of a fixed size.
         *
         * @param clientSocket  The DatagramSocket object used to send and receive data.
         * @param serverAddress The IP address of the server.
         * @param serverPort    The port number to use for communication with the server.
         * @param fileName      The name of the file to send.
         * @throws IOException If an I/O error occurs while reading the file or sending the data.
         */
        private static void sendFile(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, String fileName) throws IOException {
            // check if the file exists
            String filePath = "src/Sending Files/" + fileName;
            if (Files.exists(Paths.get(filePath))) {
                try (FileInputStream fis = new FileInputStream(filePath)) {
                    short blockNumber = 1;
                    int bytesRead;
                    byte[] dataBuffer = new byte[BUFFER_SIZE];
                    // read the file in BUFFER_SIZE chunks and send each chunk as a data packet to the server
                    while ((bytesRead = fis.read(dataBuffer)) != -1) {
                        // send the data packet to the server
                        sendData(clientSocket, serverAddress, serverPort, blockNumber, dataBuffer, bytesRead);
                        // wait for an acknowledgement packet from the server for the current block
                        receiveAck(clientSocket, blockNumber);

                        blockNumber++;
                    }
                    System.out.println("File transfer completed for " + fileName);

                } catch (IOException e) {
                    System.out.println("Error reading from file: " + e.getMessage());
                }
            }
        }


        /**
         * Receives a file from the server and saves it in the local directory.
         *
         * @param clientSocket The DatagramSocket object used to send and receive data.
         * @param fileName     The name of the file to be saved.
         * @throws IOException If an I/O error occurs while receiving or writing the file.
         */
        private static void receiveFile(DatagramSocket clientSocket, String fileName) throws IOException {
            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                 FileOutputStream fos = new FileOutputStream("src/Retrieved Files/" + fileName)) {
                short blockNumber = 1;
                boolean done = false;
                // keep receiving data packets from the server until the entire file has been received
                while (!done) {
                    byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                    DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                    try {
                        // receive a data packet from the server
                        clientSocket.receive(dataPacket);
                        // extract the block number from the data packet
                        short receivedBlockNumber = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
                        // if the block numbers match, write the data to the ByteArrayOutputStream
                        if (receivedBlockNumber == blockNumber) {
                            int dataSize = dataPacket.getLength() - 4;
                            byteArrayOutputStream.write(dataPacket.getData(), 4, dataSize);
                            // send an acknowledgement packet to the server for the current block
                            sendAck(clientSocket, dataPacket.getAddress(), dataPacket.getPort(), blockNumber);
                            blockNumber++;
                            // check if this is the last data packet for the file
                            if (dataSize < BUFFER_SIZE) {
                                done = true;
                            }
                        } else {
                            System.out.println("Received data packet with incorrect block number. Expected " + blockNumber + ", but received " + receivedBlockNumber);
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Timeout waiting for data packet for block " + blockNumber);
                        throw e;
                    }

                }

                // check if the entire file has been received
                if (blockNumber - 1 == ((byteArrayOutputStream.size() + BUFFER_SIZE - 1) / BUFFER_SIZE)) {
                    // if the entire file has been received, write the contents of the ByteArrayOutputStream to a file
                    byte[] fileContent = byteArrayOutputStream.toByteArray();
                    fos.write(fileContent);
                    System.out.println("File transfer completed for " + fileName);
                } else {
                    System.out.println("Error receiving file: incomplete data received.");
                }
            }
        }


        /**
         * Creates a WRQ (Write Request) packet for the specified file name.
         *
         * @param fileName The name of the file to create the packet for.
         * @return A byte array containing the WRQ packet for the specified file.
         */
        private static byte[] createWrqPacket(String fileName) {
            byte[] fileNameBytes = fileName.getBytes();
            // create a byte array with length equal to the length of the file name plus 4 (for the opcode and null byte)
            byte[] wrqPacket = new byte[fileNameBytes.length + 4];
            // set the first two bytes to 0 and the opcode for WRQ
            wrqPacket[0] = 0;
            wrqPacket[1] = OP_WRQ;
            // copy the bytes of the file name to the WRQ packet, starting at the third byte
            System.arraycopy(fileNameBytes, 0, wrqPacket, 2, fileNameBytes.length);
            // set the last byte of the WRQ packet to 0 to terminate the file name string
            wrqPacket[wrqPacket.length - 1] = 0;
            // return the WRQ packet as a byte array
            return wrqPacket;
        }

        /**
         * Receives the initial acknowledgment from the server.
         *
         * @param clientSocket The DatagramSocket object used to receive data.
         * @throws IOException            If an I/O error occurs while receiving the acknowledgment.
         * @throws SocketTimeoutException If a timeout occurs while waiting for the acknowledgment.
         */
        private static void receiveInitialAck(DatagramSocket clientSocket) throws IOException, SocketTimeoutException {
            // create a byte array to store the ACK packet
            byte[] ackBuffer = new byte[4];
            // create a DatagramPacket to receive the ACK packet
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
            clientSocket.setSoTimeout(5000);
            try {
                // receive the ACK packet from the server
                clientSocket.receive(ackPacket);
            } catch (SocketTimeoutException e) {
                // if the packet is not received within the timeout period, print an error message and throw an exception
                System.out.println("Timeout waiting for initial ACK.");
                throw e;
            }
            // check if the opcode of the received packet is ACK
            if (ackPacket.getData()[1] != OP_ACK) {
                // if the opcode is not ACK, print an error message and return
                System.out.println("Invalid opcode received: " + ackPacket.getData()[1]);
                return;
            }
        }


        /**
         * Sends a data packet to the TFTP server.
         *
         * @param clientSocket  the DatagramSocket used to send the packet
         * @param serverAddress the IP address of the TFTP server
         * @param serverPort    the port number of the TFTP server
         * @param blockNumber   the block number of the data packet
         * @param dataBuffer    the buffer containing the data to send
         * @param dataSize      the size of the data to send
         * @throws IOException if there is an error sending the packet
         */
        private static void sendData(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, short blockNumber, byte[] dataBuffer, int dataSize) throws IOException {
            // create a data packet containing the block number and the data to be sent
            byte[] dataPacket = createDataPacket(blockNumber, dataBuffer, dataSize);
            // create a DatagramPacket to send the data packet to the server
            DatagramPacket sendPacket = new DatagramPacket(dataPacket, dataPacket.length, serverAddress, serverPort);
            // send the data packet to the server
            clientSocket.send(sendPacket);
        }


        /**
         * Creates a data packet.
         *
         * @param blockNumber the block number of the data packet
         * @param dataBuffer  the buffer containing the data to send
         * @param dataSize    the size of the data to send
         * @return the created data packet
         */
        private static byte[] createDataPacket(short blockNumber, byte[] dataBuffer, int dataSize) {
            // create a byte array with length equal to the size of the data block plus 4 (for the opcode and block number)
            byte[] dataPacket = new byte[dataSize + 4];
            // set the first two bytes to 0 and the opcode for DATA
            dataPacket[0] = 0;
            dataPacket[1] = OP_DATA;
            // set the next two bytes to the block number, using bit shifting and masking to split the block number into two bytes
            dataPacket[2] = (byte) (blockNumber >> 8);
            dataPacket[3] = (byte) (blockNumber & 0xFF);
            // copy the bytes of the data block to the data packet, starting at the fifth byte
            System.arraycopy(dataBuffer, 0, dataPacket, 4, dataSize);
            // return the data packet as a byte array
            return dataPacket;
        }

        /**
         * Waits for an acknowledgment packet from the TFTP server.
         *
         * @param clientSocket the DatagramSocket used to receive the packet
         * @param blockNumber  the block number of the data packet to acknowledge
         * @throws IOException if there is an error receiving the packet or the packet received is invalid
         */
        private static void receiveAck(DatagramSocket clientSocket, short blockNumber) throws IOException {
            // create a byte array to store the ACK packet
            byte[] ackBuffer = new byte[4];
            // create a DatagramPacket to receive the ACK packet
            DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
            try {
                // receive the ACK packet from the server
                clientSocket.receive(ackPacket);
            } catch (SocketTimeoutException e) {
                // if the packet is not received within the timeout period, print an error message and throw an exception
                System.out.println("Timeout waiting for ACK for block " + blockNumber);
                throw e;
            }
            // check if the opcode of the received packet is ACK
            if (ackPacket.getData()[1] != OP_ACK) {
                // if the opcode is not ACK, print an error message and return
                System.out.println("Invalid opcode received: " + ackPacket.getData()[1]);
                return;
            }
            // check if the block number of the received ACK packet matches the block number of the data packet that was sent
            short receivedBlockNumber = (short) (((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF));
            if (receivedBlockNumber != blockNumber) {
                System.out.println("Received ACK packet with incorrect block number. Expected " + blockNumber + ", but received " + receivedBlockNumber);
            }
        }

        /**
         * Creates a read request packet.
         *
         * @param fileName the name of the file to read
         * @return the created read request packet
         */
        private static byte[] createRrqPacket(String fileName) {
            // convert the file name to a byte array
            byte[] fileNameBytes = fileName.getBytes();
            // create a byte array with length equal to the length of the file name plus 4 (for the opcode and null terminator)
            byte[] rrqPacket = new byte[fileNameBytes.length + 4];
            // set the first two bytes to 0 and the opcode for RRQ
            rrqPacket[0] = 0;
            rrqPacket[1] = OP_RRQ;
            // copy the bytes of the file name to the RRQ packet, starting at the third byte
            System.arraycopy(fileNameBytes, 0, rrqPacket, 2, fileNameBytes.length);
            // set the last byte to 0 (null terminator)
            rrqPacket[rrqPacket.length - 1] = 0;
            // return the RRQ packet as a byte array
            return rrqPacket;
        }

        /**
         * Sends an acknowledgment packet to the TFTP server.
         *
         * @param clientSocket  the DatagramSocket used to send the packet
         * @param serverAddress the IP address of the TFTP server
         * @param serverPort    the port number of the TFTP server
         * @param blockNumber   the block number of the data packet to acknowledge
         * @throws IOException if there is an error sending the packet
         */
        private static void sendAck(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, short blockNumber) throws IOException {
            // create an ACK packet for the given block number
            byte[] ackPacket = createAckPacket(blockNumber);
            // create a DatagramPacket to send the ACK packet to the server
            DatagramPacket sendPacket = new DatagramPacket(ackPacket, ackPacket.length, serverAddress, serverPort);
            // send the ACK packet to the server
            clientSocket.send(sendPacket);
        }

        /**
         * Creates an acknowledgment packet.
         *
         * @param blockNumber the block number of the data packet to acknowledge
         * @return the created acknowledgment packet
         */
        private static byte[] createAckPacket(short blockNumber) {
            // create a byte array with length equal to 4 (for the opcode and block number)
            byte[] ackPacket = new byte[4];
            // set the first two bytes to 0 and the opcode for ACK
            ackPacket[0] = 0;
            ackPacket[1] = OP_ACK;
            // set the third and fourth bytes to the block number (in network byte order)
            ackPacket[2] = (byte) (blockNumber >> 8);
            ackPacket[3] = (byte) (blockNumber & 0xFF);
            // return the ACK packet as a byte array
            return ackPacket;
        }

}

