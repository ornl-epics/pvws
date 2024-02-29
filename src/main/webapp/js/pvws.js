
class PVWS
{
    /** Create PV Web Socket
     * 
     *  <p>Message handler will be called with 'update'
     *  or 'error' message.
     *  The 'update' will contain the complete PV value,
     *  i.e. the merge of last known value and actual update.
     *  
     *  @param url URL of the PV web socket, e.g. "ws://localhost:8080/pvws/pv"
     *  @param connect_handler Called with true/false when connected/disconnected
     *  @param message_handler Called with each received message
     */
    constructor(url, connect_handler, message_handler)
    {
        this.url = url;
        this.connect_handler = connect_handler;
        this.message_handler = message_handler;

        // In local tests, the web socket tends to stay open indefinitely,
        // but in production setups with proxies etc. they often time out
        // after about a minute of inactivity.
        // The server side could periodically 'ping' from the SessionManager,
        // or the client could send periodic 'echo' messages.
        // We combine both approaches by having the client send ping requests
        // when the connection is idle. The server will then issue the ping,
        // and the client should return a pong (but there's no way to see that ping/pong in javascript).

        // Is the connection idle? Any received message marks as non-idle.
        this.idle = true;
        // While connected, call checkIdleTimeout() via this timer
        this.idle_timer = null;
        
        // Map of PVs to last known value,
        // merging metadata and value updates.
        this.values = {}
    }

    /** Open the web socket, i.e. start PV communication */
    open()
    {
        this.connect_handler(false);
        console.log("Opening " + this.url);
        this.socket = new WebSocket(this.url);
        this.socket.onopen = event => this.handleConnection(event);
        this.socket.onmessage = event => this.handleMessage(event.data);
        this.socket.onclose = event => this.handleClose(event);
        this.socket.onerror = event => this.handleError(event);
    }
    
    handleConnection(event)
    {
        console.log("Connected to " + this.url);
        this.connect_handler(true);

        // Start idle check
        if (this.idle_timer == null)
            this.idle_timer = setInterval(() => this.checkIdleTimeout(), this.idle_check_ms);
    }

    checkIdleTimeout()
    {
        if (this.idle)
        {
            // console.log("Idle connection " + this.url);
            this.ping();
        }
        else
        {
            // console.log("Active connection " + this.url);
            // Reset to detect new messages
            this.idle = true;
        }
    }

    stopIdleCheck()
    {
        if (this.idle_timer != null)
            clearInterval(this.idle_timer);
        this.idle_timer = null;
    }

    handleMessage(message)
    {
        // console.log("Received Message: " + message);
        this.idle = false;
        let jm = JSON.parse(message);
        if (jm.type === "update")
        {
            // Decode binary
            // TODO Assert that we always use LITTLE_ENDIAN
            if (jm.b64dbl !== undefined)
            {
                let bytes = toByteArray(jm.b64dbl);
                jm.value = new Float64Array(bytes.buffer);
                // Convert to plain array
                // When keeping the Float64Array, the JSON representation
                // will be [ "0": val0, "1": val1, ... ]
                // instead of plain array [ val0, val1, ... ]
                jm.value = Array.prototype.slice.call(jm.value);
                // console.log(jm.value);
                // console.log(JSON.stringify(jm.value));
                delete jm.b64dbl;
            }
            else if (jm.b64int !== undefined)
            {
                let bytes = toByteArray(jm.b64int);
                jm.value = new Int32Array(bytes.buffer);
                // Convert to plain array, if necessary
                jm.value = Array.prototype.slice.call(jm.value);
                delete jm.b64int;
            }
                
            // Merge received data with last known value
            let value = this.values[jm.pv];
            // No previous value:
            // Default to read-only, no data
            if (value === undefined)
                value = { pv: jm.pv, readonly: true };
            
            // Update cached value with received changes
            Object.assign(value, jm);
            this.values[jm.pv] = value;
            // console.log("Update for PV " + jm.pv + ": " + JSON.stringify(value));
            this.message_handler(value);
        }
        else
            this.message_handler(jm);
    }

    handleError(event)
    {
        console.error("Error from " + this.url);
        console.error(event);
        this.close();
    }
    
    handleClose(event)
    {
        this.stopIdleCheck();
        this.connect_handler(false);
        let message = "Web socket closed (" +  event.code ;
        if (event.reason)
            message += ", " + event.reason;
        message += ")";
        console.log(message);
        console.log("Scheduling re-connect to " +
                    this.url + " in " + this.reconnect_ms + "ms");
        setTimeout(() => this.open(), this.reconnect_ms);
    }

    /** Ask server to ping this web socket,
     *  whereupon most web clients would then reply with a 'pong'
     *  back to the server.
     */
    ping()
    {
        console.log("Sending ping to " + this.url);
        this.socket.send(JSON.stringify({ type: "ping" }))
    }
    
    /** Subscribe to one or more PVs
     *  @param pvs PV name or array of PV names
     */
    subscribe(pvs)
    {
        if (pvs.constructor !== Array)
            pvs = [ pvs ];
        this.socket.send(JSON.stringify({ type: "subscribe", pvs: pvs }));
    }

    /** Un-Subscribe from one or more PVs
     *  @param pvs PV name or array of PV names
     */
    clear(pvs)
    {
        if (pvs.constructor !== Array)
            pvs = [ pvs ];
        this.socket.send(JSON.stringify({ type: "clear", pvs: pvs }));
        
        // Remove entry for cleared PVs from this.values
        let pv;
        for (pv of pvs)
            delete this.values[pv];
    }
    
    /** Request list of PVs */
    list()
    {
        this.socket.send(JSON.stringify({ type: "list" }));
    }
    
    /** Write to PV
     *  @param pvs PV name
     *  @param value number or string
     */
    write(pv, value)
    {
        this.socket.send(JSON.stringify({ type: "write", pv: pv, value: value }));
    }
    
    /** Close the web socket.
     * 
     *  <p>Socket will automatically re-open,
     *  similar to handling an error.
     */
    close()
    {
        this.stopIdleCheck();
        this.socket.close();
    }
}

// Attempt re-connect after 10 seconds
PVWS.prototype.reconnect_ms = 10000;
// Perform idle check every 30 secs
PVWS.prototype.idle_check_ms = 30000;
