package client;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class TFTPTCPSocketClient {
    private static final int BUFFER_SIZE = 512;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;

    private static boolean running = true;

    public static void main(String[] args) throws IOException {
        // Initialize scanner for user input
        Scanner scanner = new Scanner(System.in);
        // Get server IP address and port number from user
        System.out.print("Enter the server IP address: ");
        String serverAddress = scanner.nextLine();
        System.out.print("Enter the server port number: ");
        int portNumber = scanner.nextInt();
        scanner.nextLine();
        while (running) {
            // Initialize scanner for user input
            System.out.println("**NOTE**");
            System.out.println("If you want to send a file, the file should be in the 'Sending Files' directory. To retrieve a file it should in the servers 'Retrieve Files' directory");
            try (Socket clientSocket = new Socket(serverAddress, portNumber)) {

                DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                sendHandshake(out);
                receiveHandshake(in);
                System.out.print("Enter the filename: ");
                String fileName = scanner.next();

                System.out.print("Press 1 to send the file to the server or 2 to retrieve the file from the server: ");
                int choice = scanner.nextInt();

                if (choice == 1) {
                    sendWriteRequest(out, fileName);
                    sendFile(out, fileName);
                } else if (choice == 2) {
                    sendReadRequest(out, fileName);
                    receiveFile(in, fileName);
                } else {
                    System.out.println("Invalid choice.");
                    return;
                }
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
     * Sends a handshake message to the server represented by the given DataOutputStream object.
     *
     * @param out the DataOutputStream object representing the connection to the server
     * @throws IOException if an I/O error occurs while sending the handshake message
     */
    private static void sendHandshake(DataOutputStream out) throws IOException {
        byte[] handshake = "HANDSHAKE".getBytes();
        out.write(handshake);
    }
    /**
     * Sends a write request message to the server represented by the given DataOutputStream object,
     * requesting to write the specified file.
     *
     * @param out the DataOutputStream object representing the connection to the server
     * @param fileName the name of the file to be written
     * @throws IOException if an I/O error occurs while sending the write request message
     * @throws IllegalArgumentException if fileName is null or empty
     */
    private static void sendWriteRequest(DataOutputStream out, String fileName) throws IOException {
        byte[] wrqPacket = createWrqPacket(fileName);
        out.write(wrqPacket);
    }
    /**
     * Creates a write request (WRQ) packet as specified in the TFTP protocol, containing the specified file name.
     *
     * @param fileName the name of the file to be written
     * @return a byte array representing the WRQ packet
     */
    private static byte[] createWrqPacket(String fileName) {
        byte[] fileNameBytes = fileName.getBytes();

        // Create a byte array for the WRQ packet with length of the file name plus 4 bytes.
        byte[] wrqPacket = new byte[fileNameBytes.length + 4];
        // Set the first byte to 0, indicating that this is a WRQ packet.
        wrqPacket[0] = 0;
        // Set the second byte to the opcode for WRQ (2).
        wrqPacket[1] = OP_WRQ;
        // Copy the file name bytes to the WRQ packet starting at index 2.
        System.arraycopy(fileNameBytes, 0, wrqPacket, 2, fileNameBytes.length);
        // Set the last byte to 0 to indicate the end of the file name.
        wrqPacket[wrqPacket.length - 1] = 0;
        // Return the created WRQ packet.
        return wrqPacket;
    }
    /**
     * Sends a read request message to the server represented by the given DataOutputStream object,
     * requesting to read the specified file.
     *
     * @param out the DataOutputStream object representing the connection to the server
     * @param fileName the name of the file to be read
     * @throws IOException if an I/O error occurs while sending the read request message
     */
    private static void sendReadRequest(DataOutputStream out, String fileName) throws IOException {
        fileName = new File(fileName).getName();
        byte[] rrqPacket = createRrqPacket("src/Retrieve Files/" + fileName);
        out.write(rrqPacket);
    }
    /**
     * Creates a read request (RRQ) packet as specified in the TFTP protocol, containing the specified file name.
     *
     * @param fileName the name of the file to be read
     * @return a byte array representing the RRQ packet
     */
    private static byte[] createRrqPacket(String fileName) {
        // Convert the file name to byte array.
        byte[] fileNameBytes = fileName.getBytes();
        // Create a byte array for the RRQ packet with length of the file name plus 4 bytes.
        byte[] rrqPacket = new byte[fileNameBytes.length + 4];
        // Set the first byte to 0, indicating that this is a RRQ packet.
        rrqPacket[0] = 0;
        // Set the second byte to the opcode for RRQ (1).
        rrqPacket[1] = OP_RRQ;
        // Copy the file name bytes to the RRQ packet starting at index 2.
        System.arraycopy(fileNameBytes, 0, rrqPacket, 2, fileNameBytes.length);
        // Set the last byte to 0 to indicate the end of the file name.
        rrqPacket[rrqPacket.length - 1] = 0;
        // Return the created RRQ packet.
        return rrqPacket;
    }

    /**
     * Sends the specified file to the server represented by the given DataOutputStream object.
     *
     * @param out the DataOutputStream object representing the connection to the server
     * @param fileName the name of the file to be sent
     * @throws IOException if an I/O error occurs while sending the file
     */
    private static void sendFile(DataOutputStream out, String fileName) throws IOException {
        String filePath = "src/Sending Files/" + fileName;
        if (Files.exists(Paths.get(filePath))) {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                short blockNumber = 1;
                int bytesRead;
                byte[] dataBuffer = new byte[BUFFER_SIZE];

                while ((bytesRead = fis.read(dataBuffer)) != -1) {
                    sendData(out, blockNumber, dataBuffer, bytesRead);
                    blockNumber++;
                }
                System.out.println("File transfer completed for " + fileName);

            } catch (IOException e) {
                System.out.println("Error reading from file: " + e.getMessage());
            }
        } else {
            System.out.println("File does not exist: " + fileName);
        }
    }
    /**
     * Receives the specified file from the server represented by the given DataInputStream object.
     *
     * @param in the DataInputStream object representing the connection to the server
     * @param fileName the name of the file to be received
     * @throws IOException if an I/O error occurs while receiving the file
     */
    private static void receiveFile(DataInputStream in, String fileName) throws IOException {
        // Create a byte array output stream to hold the received file data.
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream("src/Retrieved Files/" + fileName)) {
            // Set the initial block number to 1, and the "done" flag to false.
            short blockNumber = 1;
            boolean done = false;
            // While not done, read a data packet from the input stream.
            while (!done) {
                // Create a byte array buffer for the incoming data packet.
                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                // Read a data packet from the input stream, with a maximum size of BUFFER_SIZE + 4 bytes.
                int bytesRead = in.read(dataBuffer, 0, BUFFER_SIZE + 4);
                // If the end of the input stream has been reached, exit the loop.
                if (bytesRead == -1) {
                    break;
                }
                // Extract the block number from the received data packet.
                short receivedBlockNumber = (short) (((dataBuffer[2] & 0xFF) << 8) | (dataBuffer[3] & 0xFF));
                // If the received block number matches the expected block number, process the data.
                if (receivedBlockNumber == blockNumber) {
                    // Calculate the size of the data (excluding the block number and opcode).
                    int dataSize = bytesRead - 4;
                    // Write the data (excluding the block number and opcode) to the byte array output stream.
                    byteArrayOutputStream.write(dataBuffer, 4, dataSize);
                    // Increment the block number, and set the "done" flag if this is the last block.
                    blockNumber++;
                    if (dataSize < BUFFER_SIZE) {
                        done = true;
                    }
                    // If the received block number does not match the expected block number, print an error message.
                } else {
                    System.out.println("Received data packet with incorrect block number. Expected " + blockNumber + ", but received " + receivedBlockNumber);
                }
            }
            // Check if all expected blocks were received.
            if (blockNumber - 1 == ((byteArrayOutputStream.size() + BUFFER_SIZE - 1) / BUFFER_SIZE)) {
                // Convert the byte array output stream to a byte array.
                byte[] fileContent = byteArrayOutputStream.toByteArray();
                // Write the byte array to the output file.
                fos.write(fileContent);
                // Print a completion message.
                System.out.println("File transfer completed for " + fileName);

                // If not all expected blocks were received, print an error message.
            } else {
                System.out.println("Error receiving file: incomplete data received.");
            }
        } catch (IOException e) {
            // If an I/O error occurs, print an error message.
            System.out.println("Error transferring file to folder: " + e.getMessage());
        }
    }
    /**
     * Sends a data packet to the server represented by the given DataOutputStream object, containing the specified data.
     *
     * @param out the DataOutputStream object representing the connection to the server
     * @param blockNumber the block number of the data packet
     * @param dataBuffer the byte array containing the data to be sent
     * @param dataSize the size of the data in the byte array
     * @throws IOException if an I/O error occurs while sending the data packet
     */
    private static void sendData(DataOutputStream out, short blockNumber, byte[] dataBuffer, int dataSize) throws IOException {
        byte[] dataPacket = createDataPacket(blockNumber, dataBuffer, dataSize);
        out.write(dataPacket);
    }

    /**
     * Creates a data packet as specified in the TFTP protocol, containing the specified block number and data.
     *
     * @param blockNumber the block number of the data packet
     * @param dataBuffer the byte array containing the data to be sent
     * @param dataSize the size of the data in the byte array
     * @return a byte array representing the data packet
     */
    private static byte[] createDataPacket(short blockNumber, byte[] dataBuffer, int dataSize) {
        byte[] dataPacket = new byte[dataSize + 4];
        dataPacket[0] = 0;
        dataPacket[1] = OP_DATA;
        dataPacket[2] = (byte) (blockNumber >> 8);
        dataPacket[3] = (byte) (blockNumber & 0xFF);
        System.arraycopy(dataBuffer, 0, dataPacket, 4, dataSize);
        return dataPacket;
    }
    /**
     * Receives a handshake message from the server represented by the given DataInputStream object.
     *
     * @param in the DataInputStream object representing the connection to the server
     * @throws IOException if an I/O error occurs while receiving the handshake message
     */
    private static void receiveHandshake(DataInputStream in) throws IOException {
        byte[] handshakeBuffer = new byte[9];
        in.readFully(handshakeBuffer);
        String handshake = new String(handshakeBuffer);

        if (!handshake.equals("HANDSHAKE")) {
            throw new IOException("Invalid handshake received: " + handshake);
        }
    }


}

