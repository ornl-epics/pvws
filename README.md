PV Web Socket
=============

Web Socket for PVs.

Combines ideas from https://github.com/JeffersonLab/epics2web
with https://github.com/xihui/WebPDA:

 * Web Socket for EPICS Channel Access and PV Access, simulated PVs, local PVs, formulas ... based on Phoebus core-pv and RxJava
 * Scalar and array values
 * Basic values plus time stamps, status/severity, metadata for units, display ranges etc
 * Generally using JSON to simplify use in JavaScript web client, but packing array values as binary to reduce size
 * Example JavaScript client library, but usable by any web client
 * Metadata is sent once with first value, then only when it changes to reduce network traffic.
   Example JavaScript client merges updates to always present the complete value with all metadata

Key user is the [Display Builder Web Runtime](https://github.com/ornl-epics/dbwr).
Other examples include the [PV Info Tool](https://github.com/channelFinder/pvinfo).


Change Log
----------

Version information is displayed at the bottom of the built-in web page,
see `<div id="versions">` in
https://github.com/ornl-epics/pvws/blob/main/src/main/webapp/index.html

Binary
------

.. is available as https://controlssoftware.sns.ornl.gov/css_phoebus/nightly/pvws.war
but you may prefer to build it locally as described next.

Building
--------

Ideally, you build the binaries from sources because that way you can control
which version of the JDK you're using.

To build with maven:

    mvn clean package

This results in a file `target/pvws.war`.

When using VS Code, maven can be invoked from the View menu: "Command Palette", "Maven: execute commands .."
and then selecting "clean" or "package".

Project can also be imported into Eclipse JEE IDE
via File, Import, Maven, Existing Maven Projects.

This builds against a released version of core-pv and jca. To use the "latest" build from locally compiled versions of for example https://github.com/epics-base/jca.git and https://github.com/ControlSystemStudio/phoebus/tree/master/core/pva, `mvn clean install` these, then update the pom.xml to list their 1.2.3-SNAPSHOT versions, which should use the binaries that you just installed locally.

**Docker**

Edit .env file with settings for git version and port number and docker/setenv.sh with your local site settings for EPICS/web socket settings. Then:

```
docker-compose build
```

PV Types
--------

The PV web socket supports the PV names handled by core-pv, which include:

 * `ca://NameOfPV` for Channel Access
 * `pva://NameOfPV` for PV Access
 * `sim://NameOfPV` for [simulated channels](https://control-system-studio.readthedocs.io/en/latest/core/pv/doc/index.html#simulated) that may be useful for testing
 * `NameOfPV` uses the default PV type, see `PV_DEFAULT_TYPE` below
 

Running under Tomcat
--------------------

Set the following environment variables, for example in `$CATALINA_HOME/bin/setenv.sh` or `tomcat.conf`, depending on the version and installation details:

Web Socket Settings:
 * `PV_DEFAULT_TYPE`: Set to `ca` or `pva` to set the default PV type (default: `ca`).
 * `PV_THROTTLE_MS`: Throttle-latest period in milliseconds (default: 1000).
 * `PV_ARRAY_THROTTLE_MS`: .. for arrays (default: 10000).
 * `PV_WRITE_SUPPORT`: Set to `true` to enable writing (default: false).

Channel Access Settings:
 * `EPICS_CA_ADDR_LIST`: CA address list.
 * `EPICS_CA_AUTO_ADDR_LIST`: 'YES' (default) or 'NO'.
 * `EPICS_CA_MAX_ARRAY_BYTES`: CA array size.

PV Access Settings:
 * `EPICS_PVA_ADDR_LIST`: Space-separated list of host names or IP addresses. Each may be followed by ":port", otherwise defaulting to EPICS_PVA_BROADCAST_PORT. When empty, local subnet is used.
 * `EPICS_PVA_AUTO_ADDR_LIST`: 'YES' (default) or 'NO'.
 * `EPICS_PVA_BROADCAST_PORT`: Port used for name searches, defaults to 5076.
 * `EPICS_PVA_NAME_SERVERS`: Space-separated list of TCP name servers, provided as IP address followed by optional ":port". Client will connect to each address and send name searches before using the EPICS_PVA_ADDR_LIST for UDP searches. Set EPICS_PVA_ADDR_LIST to empty and EPICS_PVA_AUTO_ADDR_LIST=NO to use only the TCP name servers and avoid all UDP traffic.
 
Place `pvws.war` in `$CATALINA_HOME/webapps`.
You can check the tomcat log for the effective values of various configuration settings
since they will be logged when the context starts up.

When enabling write access, actual write access is still controlled
on a per-PV basis by Channel Access or PV Access security,
but note that the user and host seen by the CA resp. PVA server
is tomcat and not the web client end user.
So the IOC will always see a user "tomcat",
which makes it impossible to control write access based on the actual end user
via CA or PVA security.
If you decide to allow write access, you should consider placing
the web socket and any applications that utilize it (Display Builder Web Runtime, ...)
behind an authentication layer (Web Proxy, ...) which will limit access
to appropriate users. For example, configure the proxy so that users need to "log in"
before they can reach the displays. At thist time we have no commonly useful
recipe for this to share, contributions are welcome.

<details>
<summary>Note: increasing maximum message size for large PV subscribe requests</summary>

PVWS may hit into the default message size of 8192 documented in the [Tomcat documentation](https://tomcat.apache.org/tomcat-9.0-doc/web-socket-howto.html) when subscribing to a large list of PVs.

To increase this, you need to set a Servlet context initialization parameter(`org.apache.tomcat.websocket.textBufferSize`) in `<wherever tomcat is installed>/webapps/pvws/web.xml` (note this has to be done after PVWS' first startup) like so: 

```xml 
<?xml version="1.0" encoding="UTF-8"?>
<web-app>
  <display-name>pvws</display-name>
  <!-- other parameters, 404 page etc -->
  <context-param>
        <param-name>org.apache.tomcat.websocket.textBufferSize</param-name>
        <param-value>131072</param-value>
  </context-param>
</web-app>
```

In the above example we have upped the limit from 8192 to 131072 bytes per message. This number * the number of active connections your websocket can have (usually this is set in the `Connector` part of `server.xml`) must be less than the maximum size of the memory pool (`-Xmx` in the startup options, default is `256Mb`) otherwise Tomcat will throw a `java.lang.OutOfMemoryError`. 

Obviously this is only needed for subscription messages that contain lots of PVs, but if you don't want to change the tomcat settings you can just split the subscription up into smaller groups.

</details>
<br>

**Docker**

To run docker container (use -d option to run in detached mode):

```
docker-compose up
```

The status can be seen with docker ps. The status will be healthy if pvws is fully connected
```
docker ps
```

Client URLs
-----------

Open the main page of the running instance for explanation
of URLs used to connect to PVs.
Assuming Tomcat on `localhost:8080`, open

    http://localhost:8080/pvws

Web Socket Details
------------------

The basic behavior of the web socket and the fundamental format of the exchanged messages is
not expected to change. Any changes ought to remain compatible by for example adding
message elements which older clients would simply ignore.
There is, however, no guarantee of future compatibility. The exact behavior and message format
is documented on the built-in web page that demonstrates each feature.

To learn about the web socket behavior and the message format, point a web browser to

    http://localhost:8080/pvws

Enable the introspection or debug interface of your web browser. For Firefox, at the time of this
writing, invoke "Inspect" from the context menu, then reload the page to assert that you capture
all network traffic.
You should find a web socket connection to `ws://localhost:8080/pvws/pv`.
Open its Request/Response detail pane in the inspector and try the following key commands.

When you configure tomcat to allow excrypted connections, open your web browser to `https://localhost:8080/pvws`
and note that the web socket connection likewise changes to `wss://localhost:8080/pvws/pv`.

**Echo**

Note how pressing the "Echo" button on the web page sends an echo type of message to the web socket,
which then returns the same text.

**Subscribe**

Use the web page to subscribe to for example `sim://sine`.
Note the subscription request sent to the web socket, which should be similar to

    { "type": "subscribe", "pvs": [ "sim://sine" ] }

The web socket will now send 'update' replies which should resemble

    {
    "type": "update",
    "pv": "sim://sine",
    "readonly": true,
    "seconds": 1663920701,
    "nanos": 367890532,
    "units": "a.u.",
    "precision": 2,
    "min": -5,
    "max": 5,
    "warn_low": -3,
    "warn_high": 3,
    "alarm_low": -4,
    "alarm_high": 4,
    "severity": "MAJOR",
    "value": 4.755282581475768
    }
    
Note how the web socket sends the complete meta data (units etc.)
just once. The following updates then only contain the changed "value", timestamp "seconds" and "nanos",
and maybe alarm "severity".
Check the `pvws.js` library as an example for combining the received updates
into a complete value, so end users of the data can always conveniently see
the complete value while the underlying network traffic is optimized to
only transfer changes.

File Layout
===========

Maven layout is based on

    mvn archetype:generate -DgroupId=gov.ornl -DartifactId=pvws -DarchetypeArtifactId=maven-archetype-webapp -DinteractiveMode=false

