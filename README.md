# In-Memory Key-Value Cache Service

A high-performance, in-memory key-value cache service built with Java, Spring Boot, and Netty. This implementation focuses on maximum throughput and minimal latency for get/put operations.

## Overview

This service provides a simple but highly optimized in-memory key-value cache with HTTP endpoints for `put` and `get` operations. The implementation includes several performance optimizations to handle heavy loads while maintaining low latency.

## Features

- **High-performance**: Optimized for throughput and low latency
- **Memory-efficient**: Includes automatic cache eviction strategies
- **Concurrency**: Thread-safe with sharded locks for minimal contention
- **Scalability**: Efficiently utilizes available CPU cores
- **Monitoring**: Basic statistics tracking for gets, puts, and cache misses

## API Endpoints

### 1. PUT Operation

**HTTP Method**: POST  
**Endpoint**: `/put`  
**Request Body**:
```json
{
  "key": "string (max 256 characters)",
  "value": "string (max 256 characters)"
}
```

**Success Response** (HTTP 200):
```json
{
  "status": "OK",
  "message": "Key inserted/updated successfully."
}
```

### 2. GET Operation

**HTTP Method**: GET  
**Endpoint**: `/get?key=exampleKey`  
**Parameters**: A query parameter named `key`

**Success Response** (HTTP 200):
```json
{
  "status": "OK",
  "key": "exampleKey",
  "value": "the corresponding value"
}
```

**Key Not Found Response**:
```json
{
  "status": "ERROR",
  "message": "Key not found."
}
```

## Technical Design

### Performance Optimizations

1. **Sharded Cache**:
   - The cache is divided into 512 shards to reduce lock contention
   - Each shard has its own dedicated read-write lock

2. **Concurrent Data Structures**:
   - Uses `ConcurrentHashMap` for thread safety
   - Atomic operations for counters and references

3. **Memory Management**:
   - LRU (Least Recently Used) eviction strategy
   - Automatic memory monitoring to prevent OOM errors
   - Batch eviction process to maintain performance under load

4. **Netty-based HTTP Server**:
   - Event-driven, non-blocking I/O for high throughput
   - Separate thread pools for connection handling and business logic
   - Optimized buffer allocations and TCP settings

5. **JVM Tuning**:
   - G1 garbage collector with tuned parameters
   - String deduplication for memory efficiency
   - Memory limit controls to prevent container issues

### Cache Eviction Strategy

The system implements an LRU (Least Recently Used) eviction policy with the following characteristics:

- Memory usage is monitored at regular intervals (every 1 second)
- When memory usage exceeds 70% or the entry count exceeds 5 million, eviction is triggered
- Eviction happens in batches of 5000 entries to maintain performance
- A priority queue is used to efficiently identify the least recently accessed entries

## Building and Running

### Prerequisites

- JDK 17+
- Maven
- Docker

### Build and Run with Docker

1. Clone the repository:
```bash
git clone https://github.com/neeldholiya04/key-value-cache.git
cd key-value-cache
```

2. Build the Docker image:
```bash
docker build -t key-value-cache .
```

3. Run the Docker container:
```bash
docker run -p 7171:8181 key-value-cache
```

The service will be available at `http://localhost:7171`.

### Build and Run Locally

1. Build with Maven:
```bash
./mvnw clean package
```

2. Run the application:
```bash
java -jar target/redis-0.0.1-SNAPSHOT.jar
```

## Performance Considerations

### Thread Pool Sizing

- **Boss threads**: Manages incoming connections (CPUs/2)
- **Worker threads**: Handles socket I/O (CPUs*2)
- **Business Logic threads**: Processes requests (CPUs*4)

### Memory Usage

The cache is configured to:
- Use a maximum of 70% of available heap memory
- Store up to 5 million entries
- Evict entries proactively to maintain performance

### TCP Optimizations

- Increased connection backlog (16384)
- Enabled TCP_NODELAY for reduced latency
- Increased socket buffers for higher throughput
- Connection timeout handling to free resources
