
class PVWS
{
    constructor(url)
    {
        this.url = url;
    }

    open()
    {
        this.socket = new WebSocket(this.url);
        this.socket.onopen = message => console.log("Connected to " + this.url);
        this.socket.onmessage = message => console.log("Received " + message.data);
        this.socket.onclose = message => this.handleClose();
        this.socket.onerror = message => console.log("Error: " + message);
    }
    
    handleClose()
    {
        console.log("Web socket was closed: " + this.url);
        console.log("Scheduling re-connect...");
        setTimeout(this.open, 1000);
    }
    
    close()
    {
        this.socket.close();
    }
}

