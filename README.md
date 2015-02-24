# TwitterSim
## Twitter Simulator
This is a one time project that can be used to load-test a server. This consists of a server-client system which mimics the fundamental features of a twitter service.
The following are the features covered:

1. Generation of clients by the server
2. Activating clients where the clients tweet the string (More details in Report.txt).
3. Processing the tweets to classify it into three different types
  * Mentions
  * Tweet
  * Personal messages
  
  Refer [this] (http://www.momthisishowtwitterworks.com/) for more features about the implementation
4. Designing the client graphs (who follows whom; random assignment)
5. Rate at which the individual clients tweet; not all clients tweet at the same rate and at the same time.
6. Peak hour assignment - The tweets generated may vary during peak hours.
  [Reference] (http://www.internetlivestats.com/twitter-statistics/)

## Phases
1. Implementation in AKKA framework.
2. Wrap the features using a REST-API using Spray-can

## Flow
The below explains about the sequence of flow in designing a twitter simulator:

1. The user has to decide on the following parameters
  * Number of Servers
  * Number of Clients
  * Maximum number of neighbors (follower+following)
  * Total number of messages
  * Average number of followers
  * Average TweetLength
  * Maximum number of mentions in a tweet
  * Peak time start, end, scale factor
  * Message limit for each client (Recent n messages)
2. The server generates the clients based on the parameters mentioned above and the clients are activated from the server.
3. Once the server activates the client, the client generates tweets and sends to the server. 
4. The server processes the tweet and stores the tweet (mention, recent messages, personal messages).
5. When the total number of messages are processed, each client requests the recent messages and the server returns the tweets based on the clients.
6. The client displays the required information.

## Possible Extensions
* All the features of twitter is not implemented; only a selected few for testing. 
* Since this is a simulation project, there are a few parameters which have to be decided at the server and therefore may not be necessary when implementing as a project.
* Twitter statistics vary upon time and therefore requires frequent updation which is beyond the scope of the project.
* The design may not pertain to a certain number of tweets which is the stopping criteria in this project. Some other parameters to be considered are time, updation of messages at frequent intervals.
* Persistent storage of tweets is necessary. For example, this project covers recent n messages and any n+1th message (or more) is ignored. Requirements can be modified where the tweets can be stored to a extenal storage medium and regular updation of the tweets when the client requests it.
* Communication between multiple servers is negligible in terms of knowledge sharing in this simulation and can be mofidifed accordingly where several servers can identify bots.
