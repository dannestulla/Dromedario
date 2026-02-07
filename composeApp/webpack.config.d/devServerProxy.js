// Move webpack dev server's HMR WebSocket to /webpack-ws so it doesn't conflict with our /ws proxy
if (config.devServer) {
    config.devServer.webSocketServer = {
        type: 'ws',
        options: {
            path: '/webpack-ws'
        }
    };
    config.devServer.client = Object.assign({}, config.devServer.client, {
        webSocketURL: {
            pathname: '/webpack-ws'
        }
    });

    // Enable SPA fallback for client-side routing (e.g., /export)
    config.devServer.historyApiFallback = true;

    // Proxy API and WebSocket requests to the Ktor backend
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://127.0.0.1:8080',
            changeOrigin: true
        },
        {
            context: ['/ws'],
            target: 'http://127.0.0.1:8080',
            ws: true,
            changeOrigin: true
        }
    ];
}
