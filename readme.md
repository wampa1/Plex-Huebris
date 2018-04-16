Create environment variables for each of the following:

PLEX_SERVER

PLEX_PORT

PLEX_TOKEN

HUE_SERVER

HUE_USER

https://developers.meethue.com/documentation/getting-started

To Register a new Device:

Post to:
http://<bridge ip address>/api
{"devicetype":"plex-huebris#mac"}

First response will be a "Button is not pressed response"

After pressing button:

Success Response (after pressing button and re-posting)
[
	{
		"success": {
			"username": "<<REDACTED>>"
		}
	}
]

To get information about the lights:

GET to 
/api/REDACTED/lights

Need to get your X-PLEX-TOKEN
https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/

Configure a web hook in Plex to route to where this is running, port 8080 /api/hook
https://support.plex.tv/articles/115002267687-webhooks/