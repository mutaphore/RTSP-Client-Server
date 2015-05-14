JFLAGS = -cp .:./Fsm/
JC = javac
J = java

all: TCPState.class TCPEvent.class TCPAction.class Main.class
	$(J) $(JFLAGS) Main
Main.class: Main.java TCPEvent.java TCPState.java TCPAction.java
	$(JC) $(JFLAGS) Main.java TCPEvent.java TCPState.java TCPAction.java
TCPState.class: TCPState.java
	$(JC) $(JFLAGS) TCPState.java
TCPEvent.class: TCPEvent.java TCPAction.java
	$(JC) $(JFLAGS) TCPEvent.java TCPAction.java
TCPAction.class: TCPAction.java
	$(JC) $(JFLAGS) TCPAction.java
clean:
	rm -f *.class
