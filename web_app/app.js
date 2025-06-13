// app.js
const express = require('express');
const path = require('path');
const bodyParser = require('body-parser');
const axios = require('axios');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.static(path.join(__dirname, 'public')));
app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

// Fake user database with ThingsBoard device IDs
const users = [
  { 
    id: 1, 
    username: 'alice', 
    password: 'pass123', 
    deviceToken: 'jjl0jhykAJF1miCTHxMB',
    deviceId: '353caeb0-416f-11f0-a544-db21b46190ed'
  },
  { 
    id: 2, 
    username: 'bob', 
    password: 'bobpass', 
    deviceToken: 'jjl0jhykAJF1miCTHxMB',
    deviceId: '353caeb0-416f-11f0-a544-db21b46190ed'
  },
  { 
    id: 3, 
    username: 'carol', 
    password: 'carolpw', 
    deviceToken: 'carol-device-token',
    deviceId: 'carol-device-id'
  }
];

// ThingsBoard configuration
const THINGSBOARD_HOST = '<IP:PORT>'; // Replace with actual ThingsBoard host (e.g., http://localhost:8080)
const TENANT_USERNAME = '<USERNAME>'; // Replace with actual tenant username
const TENANT_PASSWORD = '<PASSWORD>'; // Replace with actual tenant password

// Store JWT tokens temporarily (in production, use proper session management)
const userTokens = new Map();

// Serve login page
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'login.html'));
});

// Handle login - authenticate with ThingsBoard and store JWT token
app.post('/login', async (req, res) => {
  const { username, password } = req.body;
  const user = users.find(u => u.username === username && u.password === password);
  
  if (!user) {
    return res.send('<h2>Invalid credentials. <a href="/">Try again</a></h2>');
  }

  try {
    // Authenticate with ThingsBoard to get JWT token
    const authResponse = await axios.post(`${THINGSBOARD_HOST}/api/auth/login`, {
      username: TENANT_USERNAME,
      password: TENANT_PASSWORD
    });

    const jwtToken = authResponse.data.token;
    
    // Store the JWT token for this user session
    userTokens.set(user.id, jwtToken);
    
    res.redirect(`/user/${user.id}?username=${user.username}`);
  } catch (error) {
    console.error('ThingsBoard authentication failed:', error.message);
    res.send('<h2>Authentication with ThingsBoard failed. <a href="/">Try again</a></h2>');
  }
});

app.get('/user/:id', (req, res) => {
  const user = users.find(u => u.id === parseInt(req.params.id));
  if (user && userTokens.has(user.id)) {
    res.sendFile(path.join(__dirname, 'dashboard.html'));
  } else {
    res.send('<h2>User not found or not authenticated</h2>');
  }
});

// Get user info including username
app.get('/api/user/:id/info', (req, res) => {
  const user = users.find(u => u.id === parseInt(req.params.id));
  if (user) {
    res.json({ 
      username: user.username,
      deviceId: user.deviceId 
    });
  } else {
    res.status(404).json({ error: 'User not found' });
  }
});

// Protected endpoint to fetch device data using JWT token
app.get('/api/user/:id/device-data', async (req, res) => {
  const userId = parseInt(req.params.id);
  const user = users.find(u => u.id === userId);
  const jwtToken = userTokens.get(userId);

  console.log(`Fetching data for user ${userId}, deviceId: ${user?.deviceId}`);
  console.log(`JWT token exists: ${!!jwtToken}`);

  if (!user || !jwtToken) {
    console.log('Missing user or JWT token');
    return res.status(401).json({ error: 'Unauthorized' });
  }

  try {
    const telemetryUrl = `${THINGSBOARD_HOST}/api/plugins/telemetry/DEVICE/${user.deviceId}/values/timeseries`;
    
    console.log('Requesting URL:', telemetryUrl);
    
    const response = await axios.get(telemetryUrl, {
      headers: {
        'X-Authorization': `Bearer ${jwtToken}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });

    console.log('Response status:', response.status);
    console.log('Response data:', response.data);
    res.json(response.data);
  } catch (error) {
    console.error('Error fetching device data:');
    console.error('Status:', error.response?.status);
    console.error('Status text:', error.response?.statusText);
    console.error('Data:', error.response?.data);
    console.error('Full error:', error.message);
    
    // If token expired, try to refresh
    if (error.response && error.response.status === 401) {
      try {
        console.log('Token expired, refreshing...');
        // Re-authenticate to get new token
        const authResponse = await axios.post(`${THINGSBOARD_HOST}/api/auth/login`, {
          username: TENANT_USERNAME,
          password: TENANT_PASSWORD
        });

        const newJwtToken = authResponse.data.token;
        userTokens.set(userId, newJwtToken);
        console.log('New token obtained, retrying request...');

        // Retry the request with new token
        const retryResponse = await axios.get(telemetryUrl, {
          headers: {
            'X-Authorization': `Bearer ${newJwtToken}`,
            'Content-Type': 'application/json',
            'Accept': 'application/json'
          }
        });

        res.json(retryResponse.data);
      } catch (retryError) {
        console.error('Token refresh failed:', retryError.message);
        res.status(500).json({ error: 'Authentication failed' });
      }
    } else {
      res.status(error.response?.status || 500).json({ 
        error: 'Could not fetch device data',
        details: error.response?.data || error.message
      });
    }
  }
});

// Alternative endpoint using device token
app.get('/api/user/:id/device-data-simple', async (req, res) => {
  const userId = parseInt(req.params.id);
  const user = users.find(u => u.id === userId);

  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }

  const deviceToken = user.deviceToken;
  const url = `${THINGSBOARD_HOST}/api/v1/${deviceToken}/telemetry`;

  console.log('Simple endpoint URL:', url);

  try {
    const response = await axios.get(url, {
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });
    console.log('Simple endpoint response:', response.data);
    res.json(response.data);
  } catch (err) {
    console.error('Error fetching from ThingsBoard simple endpoint:', err.response?.data || err.message);
    res.status(err.response?.status || 500).json({ 
      error: 'Could not fetch device data',
      details: err.response?.data || err.message
    });
  }
});

// Send command to ThingsBoard device
app.post('/api/user/:id/send-command', async (req, res) => {
  const userId = parseInt(req.params.id);
  const user = users.find(u => u.id === userId);
  const jwtToken = userTokens.get(userId);
  const { command, params } = req.body;

  console.log(`Sending command for user ${userId}, deviceId: ${user?.deviceId}`);
  console.log(`Command: ${command}, Params:`, params);

  if (!user || !jwtToken) {
    console.log('Missing user or JWT token');
    return res.status(401).json({ error: 'Unauthorized' });
  }

  try {
    // Method 1: Send RPC command using JWT token
    const rpcUrl = `${THINGSBOARD_HOST}/api/plugins/rpc/oneway/${user.deviceId}`;
    
    const rpcPayload = {
      method: command,
      params: params,
      timeout: 5000
    };

    console.log('Sending RPC command to:', rpcUrl);
    console.log('RPC Payload:', rpcPayload);
    
    const response = await axios.post(rpcUrl, rpcPayload, {
      headers: {
        'X-Authorization': `Bearer ${jwtToken}`,
        'Content-Type': 'application/json',
        'Accept': 'application/json'
      }
    });

    console.log('RPC Response status:', response.status);
    console.log('RPC Response data:', response.data);
    res.json({ success: true, response: response.data });
    
  } catch (error) {
    console.error('Error sending RPC command:');
    console.error('Status:', error.response?.status);
    console.error('Data:', error.response?.data);
    
    // Fallback: Try using device token to send telemetry/attributes
    try {
      console.log('RPC failed, trying device token approach...');
      const deviceToken = user.deviceToken;
      const telemetryUrl = `${THINGSBOARD_HOST}/api/v1/${deviceToken}/telemetry`;
      
      // Send as telemetry data
      const telemetryPayload = {
        [`${command}_timestamp`]: Date.now(),
        ...params
      };

      const telemetryResponse = await axios.post(telemetryUrl, telemetryPayload, {
        headers: {
          'Content-Type': 'application/json'
        }
      });

      console.log('Telemetry response:', telemetryResponse.status);
      res.json({ success: true, method: 'telemetry', response: telemetryResponse.data });
      
    } catch (telemetryError) {
      console.error('Both RPC and telemetry methods failed:', telemetryError.message);
      res.status(500).json({ 
        error: 'Could not send command',
        details: error.response?.data || error.message
      });
    }
  }
});

app.listen(PORT, () => {
  console.log(`Server is running at http://localhost:${PORT}`);
});