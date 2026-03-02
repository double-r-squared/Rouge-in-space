# Rogue In Space

#### To Run: 

sudo apt install sqlite3

./setup.sh

# Download the self-contained version
curl -L -o sqlite-jdbc-3.36.0.3.jar \
https://github.com/xerial/sqlite-jdbc/releases/download/3.36.0.3/sqlite-jdbc-3.36.0.3.jar

# Recompile
javac -cp .:sqlite-jdbc-3.36.0.3.jar *.java

# Run
java -cp .:sqlite-jdbc-3.36.0.3.jar Game

---
# For Team Use:
rm -f *.class
to clear binary files, the repo is flat so these might get in the way