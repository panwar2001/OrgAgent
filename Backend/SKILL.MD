AI RAG chatbot for company/organization internal documents for different sets of projects.
Each organization account  can create different projects.
Each project can accept ingestion files to have a chatbot for.

create feature based architecture
framework: spring boot , java 25
enable virtual thread

postgresql with pgvector will be used for rag
use the libraries which are already provided be scoped with these libraries given, discuss if anything else
needed to build the rag.
add flyway library to initialize / migrate schemas.

Generate a core folder consisting of Config , exception , redis , security for security config.

Config => CorsConfig, FlywayConfig, SwaggerConfiguration
exceptions => global rest exception handler with all the exceptions
redis => redis configurations
security => securityFilterChain

rest are feature modules

use Redis to fetch live chat : Retrieve last
rag context : perform vector search in postgres using embedded user query.

combine chat history + RAG context to send payload to LLM

llm generates the response => async write to redis ( user prompt + ai reply ) , async write to postgres( append integration log to chat_history )

logs of chats will be written to postgres , redis will maintain window of latest chats like latest 10 - 20 chats ( rolling window ) ,
so that on every new ai request these chats are fetched from the redis and if there is no semantic cache of the query available then ask the AI to generate response.
after that rolling cache of the last N queries is updated and the semantic cache is also updated. if query was already present in semantic cache then
return that response from the cache.


compose file :
use latest postgres:18-alpine , redis:8-alpine

create docker file


Commit reminder: do not generate all at once , commit by adding valuable code and running test cases
small & valuable meaning full code that builds and dont throw compiler / build error.
Test driven development is highly perceived to engrossed. 
