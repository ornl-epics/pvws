
class PVWS
{
    constructor(url)
    {
        this.url = url;
    }

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
    
    close()
    {
        this.socket.close();
    }
}

// TODO Larger timeout for production setup
PVWS.prototype.reconnect_ms = 1000;