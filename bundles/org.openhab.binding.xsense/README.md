# X-Sense Binding

This binding integrates [X-Sense](https://www.x-sense.com) home safety devices into openHAB.
X-Sense offers smoke detectors, carbon monoxide (CO) detectors, heat alarms, water leak sensors and thermo-hygrometers, plus a security line with door/window sensors, motion sensors, keypads, alarm listeners, driveway/mailbox alarms and strobe lights.
Sensors are connected through the X-Sense SBS50 Base Station, which links the battery powered sensors via a proprietary RF protocol and connects to the network via 2.4 GHz Wi-Fi.

## Scope and Cloud Usage

The X-Sense system does not provide a documented local API.
Communication is therefore performed through the X-Sense cloud services using the credentials of your X-Sense account.
The binding polls the device inventory via the cloud REST API; real-time state updates (alarms, sensor values) will be added with the upcoming MQTT support.
All cloud communication uses HTTPS.
A future version may add local communication once a local API becomes available.

## Binding Configuration

The binding configuration (Settings → Add-on Settings → X-Sense Binding) provides defaults for all accounts:

| Name            | Type    | Description                                                           | Default |
|-----------------|---------|-----------------------------------------------------------------------|---------|
| refreshInterval | integer | Default interval in seconds for polling the device inventory (min 30) | 300     |

Account things inherit this value unless they configure their own interval.

## Supported Things

| Thing Type UID     | Type   | Description                                        |
|--------------------|--------|----------------------------------------------------|
| xsense:account     | Bridge | Represents your X-Sense account (cloud connection) |

## Thing Configuration

### `account` Bridge Configuration

| Name            | Type    | Description                                                    | Default         | Required | Advanced |
|-----------------|---------|-----------------------------------------------------------------|-----------------|----------|----------|
| email           | text    | Email address of your X-Sense account                          | N/A             | yes      | no       |
| password        | text    | Password of your X-Sense account                               | N/A             | yes      | no       |
| refreshInterval | integer | Interval in seconds for polling the device inventory (min 30)  | binding default | no       | yes      |

### Full Example (`.things` file)

Things can be created through auto-discovery (see below, added in a later change) or manually via a `.things` file.

```java
Bridge xsense:account:myaccount "My X-Sense Account" [ email="user@example.com", password="secret" ]
```
