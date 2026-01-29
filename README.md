#  Proxy Server Implementation

Here I build a Java-based cached proxy server implementation based on the examples from 
**Herbert Schildt's "Java 2: The Complete Reference"**.

## Purpose
This repository is a personal educational project to understand:
- Low-level socket programming in Java.
- Manual HTTP MimeHeader parsing.
- Socket-Level Fundamentals: To explore the mechanics of the java.net.ServerSocket and Socket classes before the abstraction provided by modern frameworks like Netty or Spring.

- Protocol Analysis: To manually implement the HTTP/1.0 request-response lifecycle, focusing on byte-stream management and header parsing.

- Concurrency Study: To analyze the "one-thread-per-connection" model and its implications on system resource allocation (memory and CPU).

## Technical Notes
- **Language:** Java
- **Target JRE:** 1.8 (Java 8)
- Parsing Heuristics: The use of StringTokenizer and DataInputStream.readLine() reflects the limitations of early JDKs regarding character encoding and robust metadata extraction.

  - Note: This proxy is designed for plain-text HTTP only.

## Disclaimer
This code is for educational purposes and is adapted from the
original technical examples.
