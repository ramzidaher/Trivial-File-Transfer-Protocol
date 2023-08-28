# TFTP UDP & TCP Implementation README

## Introduction

This repository provides the implementation for a Trivial File Transfer Protocol (TFTP) client and server using both UDP and TCP protocols in Java.

## Note
This project is a university assignment and is intended solely for educational purposes. Redistribution or use of this code outside the context of the coursework is not endorsed. If you choose to use any part of this project, you do so at your own risk. The author assumes no responsibility for any consequences arising from the use or misuse of this code.

## Components

1. **TFTPUDPSocketClient** (UDP Client)
2. **TFTP UDPSocketServer** (UDP Server)
3. **TFTPTCPSocketClient** (TCP Client)
4. **TFTPTCPSocketServer** (TCP Server)

### 1. TFTPUDPSocketClient (UDP Client)

- A Java-based UDP client to send and receive files from a TFTP server.
- Users input the server's IP, port number, and desired filename for transfer.
- Files to be sent are located in the "Sending Files" directory.
- Files received are saved to the local "Retrieve Files" directory.
- The client communicates with the server using the TFTP protocol, ensuring reliable data transfer over an inherently unreliable transport protocol, UDP.
- Error packets from the server are handled gracefully, informing the user of the error.

### 2. TFTP UDPSocketServer (UDP Server)

- A Java UDP server that listens for incoming TFTP read (RRQ) and write (WRQ) requests.
- Efficient and robust, designed using modular helper methods to ensure smooth file transfers in accordance with TFTP specifications.
- Listens for incoming packets, processes client data, and sends appropriate file data or acknowledgment (ACK) packets.
- Handles errors by sending client an ERROR packet.

### 3. TFTPTCPSocketClient (TCP Client)

- Users must provide the server's address (typically 'localhost') and port number.
- Client connects using TCP for reliable communication and uses handshakes unique to each transaction.
- The user inputs determine read or write requests.
- Data packets are checked for the correct block number, and files are received or sent accordingly.
- Timeouts, resending packets, and transaction termination are handled effectively.

### 4. TFTPTCPSocketServer (TCP Server)

- Listens for incoming TCP connections and handles each client request in a new thread.
- Initiates handshakes with clients and reads incoming packets for opcodes (OP_WRQ or OP_RRQ).
- Retrieves or writes files based on client requests.
- Performs error handling, including sending error packets for invalid handshakes, incorrect block numbers, or issues during file writing.

## Usage

### Starting a Client

1. Navigate to the directory containing the client files.
2. Run `java TFTPSocketClient [protocol] [server_address] [port_number]`
    - Replace `[protocol]` with `UDP` or `TCP` depending on desired protocol.
    - Replace `[server_address]` with the server's address.
    - Replace `[port_number]` with the desired port number.

### Starting a Server

1. Navigate to the directory containing the server files.
2. Run `java TFTPSocketServer [protocol] [port_number]`
    - Replace `[protocol]` with `UDP` or `TCP` depending on desired protocol.
    - Replace `[port_number]` with the desired port number.

## Limitations

- The UDP implementation assumes a relatively stable network. In environments with high packet loss, performance may degrade.
- Current implementation assumes files are present in predefined directories for both the client and server. Ensure files are placed correctly before initiating transfers.
- This TFTP implementation is basic and does not support advanced features or optimizations found in more comprehensive TFTP solutions.

## Feedback

If you find any bugs or have suggestions, please open an issue in this repository. We welcome community contributions and improvements.

## License

This project is licensed under the MIT License.
