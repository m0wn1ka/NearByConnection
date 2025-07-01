# Share with neighbours
## requirements
- both mobile devices are connected to same wifi
## use case
- we dont need the nearby users phone number or any other detail
- we can share our own msg with all nearby(around 50meters) users
## structure
- mainly it has 3 parts
- part 1 ->user gives their msg
- part2 ->to press advertise button(share news button)
- part3->will show what the other user gave(what we get in discovery)
## techonolgy used (high level)
- kotlin -> data store, neary by connections api
## how to use
- install the app
- can update the msg as per our wish
- click the share msg button
- all near by devices who just opened the app will get this msg
## how it works
- using neary by connections we can communicate with near by devices with wifi
- a similar example would be share me app
- in android manifest file defined all necessary permissions
- on activity creation we create client connectionsClient
- the text box (user can typer here) will be set to a default value/previously saved value
- permissions will be requested as per need
- in the last part of ui we see a text view, the value will be set at creation (fetched from data store(dict))
- at creation itself we call start discovering 
- so this listens for connections
- when user clicks the share msg button , advertise will happen
- so while advertising if any other devices is in discovery mode, a connection initiation request will go
- we can make the start discovery in a setInterval manner but it would consume power, so for now that is not included
