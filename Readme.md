PV Web Socket
=============

Building
--------

    export ANT_HOME=/path/to/apache-ant
    export CATALINA_HOME=/path/to/apache-tomcat
    export JAVA_HOME=/path/to/jdk8
    export PATH=$ANT_HOME/bin:$JAVA_HOME/bin:$PATH
    
    ant clean war

Running under Tomcat
--------------------

Place `pvws.war` in `$CATALINA_HOME/webapps`


Client URLs
-----------

Open the main page of the running instance for explanation
of URLs used to connect to PVs.
Assuming Tomcat on `localhost:8080`, open

    http://localhost:8080/pvws
    

Development Status
==================

Mostly Functional
-----------------

 * Start page that explains API
 * Imported code for VType.PV (ca, sim, loc)


TODO
----

 * Send data to clients
      - Sends initial metadata, then updates. Client lib keeps complete data.
      - JSON, except some binary encoding for array values
 * Clear PVs when session closes or tomcat reloads web application
