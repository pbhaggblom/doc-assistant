# Documentation Assistant

This is an AI-assistant built with Quarkus and LangChain4j, and it is my capstone project for the Java Developer program at Nackademin. 

With data integrity and resource management in mind, the goal of the project was to build a completely local system with an assistant that can answer questions based on a provided documentation through Retrieval Augmented Generation. The system consists of a CLI, which is the user interface installed locally, and the backend running in your home Kubernetes cluster.

## Tech Stack

- Framework: Quarkus (Java) and Mutiny

- AI integration: LangChain4j

- LLM: llama3.2:3B

- Embedding model: mxbai-embed-large

- Vector database: ChromaDB

- Communication protocol: gRPC

- Security: Keycloak

## Functionality

### Ingestion
- Splits, cleans and ingests Markdown-files
- Filters out unnecessary files
- Detects changes in files through hashing, and skips unchanged files

### Documentation assistant
- Recieves questions from the user and retrieves relevant context from the vector database
- Replies strictly based on given provided context

### Asynchronous Real-Time Streaming
- Responses from the backend are streamed back to the CLI with gRPC and Mutiny

## Getting started

### Ollama
- Make sure Ollama is running in your cluster and pull the correct models
```bash
ollama pull llama3.2:3b
ollama pull mxbai-embed-large
```
### Keycloak
- Login to your Keycloak instance, create a realm called doc-assistant-realm and add an admin user.

### Hostnames
- Add the following hostnames to your hosts-file
```bash
assistant.doc-assistant
docs.doc-assistant
keycloak.local
```

### Documentation
- Change the volume path in the documentation-deployment.yaml to the location of your documentation
- I have used Kubernetes documentation from https://github.com/kubernetes/website/tree/main/content/en/docs and the filtering in DocumentIngestionService.java is based on that

### Using the CLI
- create a jar or native executable and run in your terminal
```bash
doc ask <question> # Ask a question
doc status # Check ingestion status
doc ingest # Start ingestion
doc stop # Stop ingestion
doc reset # Clear vector database
```