
class PVWS
{
    /** Create PV Web Socket
     *  @param url URL of the PV web socket, e.g. "ws://localhost:8080/pvws/pv"
     */
    constructor(url)
    {
        this.url = url;
    }

    /** Open the web socket, i.e. start PV communication */
    open()
    {
        console.log("Opening " + this.url);
        this.socket = new WebSocket(this.url);
        this.socket.onopen = event => this.handleConnection();
        this.socket.onmessage = event => this.handleMessage(event.data);
        this.socket.onclose = event => this.handleClose(event);
        this.socket.onerror = event => this.handleError(event);
    }
    
    handleConnection()
    {
        console.log("Connected to " + this.url);
    }
    
    handleMessage(message)
    {
        console.log("Received Message: " + message);
    }

    handleError(event)
    {
        console.error("Error from " + this.url);
        console.error(event);
        this.close();
    }
    
    handleClose(event)
    {
        let message = "Web socket closed (" +  event.code ;
        if (event.reason)
            message += ", " + event.reason;
        message += ")";
        console.log(message);
        console.log("Scheduling re-connect to " +
                    this.url + " in " + this.reconnect_ms + "ms");
        setTimeout(() => this.open(), this.reconnect_ms);
    }
    
    /** Close the web socket.
     * 
     *  <p>Socket will automatically re-open,
     *  similar to handling an error.
     */
    close()
    {
        this.socket.close();
    }

    /**  */
    ping()
    {
        this.socket.send(JSON.stringify({ type: "ping" }))
    }
    
    /** Subscribe to one or more PVs
     *  @param pvs PV name or array of PV names
     */
    subscribe(pvs)
    {
        if (pvs.constructor !== Array)
            pvs = [ pvs ];
        // TODO Remember all PVs so we can re-subscribe after close/re-open
        this.socket.send(JSON.stringify({ type: "subscribe", pvs: pvs }))
    }
}

// TODO Larger timeout for production setup
PVWS.prototype.reconnect_ms = 1000;
