
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
        this.socket.onopen = event => this.handleConnection();
        this.socket.onmessage = event => this.handleMessage(event.data);
        this.socket.onclose = event => this.handleClose(event);
        this.socket.onerror = event => this.handleError(event);
    }
    
    handleConnection()
    {
        console.log("Connected to " + this.url);
        this.connect_handler(true);
    }
    
    handleMessage(message)
    {
        // console.log("Received Message: " + message);
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
        this.socket.send(JSON.stringify({ type: "subscribe", pvs: pvs }));
    }

    /** Un-Subscribe from one or more PVs
     *  @param pvs PV name or array of PV names
     */
    clear(pvs)
    {
        if (pvs.constructor !== Array)
            pvs = [ pvs ];
        // TODO Forget PVs so we don't re-subscribe after close/re-open
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
        this.socket.close();
    }
}

// TODO Larger timeout for production setup
PVWS.prototype.reconnect_ms = 5000;
