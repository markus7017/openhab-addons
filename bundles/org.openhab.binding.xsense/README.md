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

The binding maps the X-Sense cloud data model to a four level thing hierarchy: `account` → `home` → `station` → sensor.
Multiple accounts, multiple homes per account, multiple base stations per home and multiple sensors per station are supported.

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
| xsense:home        | Bridge | A home (house) registered in the account           |
| xsense:station     | Bridge | X-Sense SBS50 Base Station                         |
| xsense:smoke       | Thing  | Smoke detector (e.g. XS01-M, XS0B-MR, XS0D-MR)     |
| xsense:co          | Thing  | Carbon monoxide detector (e.g. XC01-M)             |
| xsense:smokeco     | Thing  | Combined smoke/CO detector (e.g. SC07-MR, XP0A-MR) |
| xsense:heat        | Thing  | Heat detector (e.g. XH02-M)                        |
| xsense:water       | Thing  | Water leak detector (e.g. SWS51, SWS54)            |
| xsense:thermohygro | Thing  | Thermo-hygrometer (e.g. STH51, STH0A, STH0B)       |
| xsense:listener    | Thing  | Alarm listener (SAL51, SAL100)                     |
| xsense:driveway    | Thing  | Driveway alarm (SDA51)                             |
| xsense:mailbox     | Thing  | Mailbox alarm (SMA51, SMA11)                       |
| xsense:door        | Thing  | Door/window sensor (SDS0A, SES01)                  |
| xsense:motion      | Thing  | Motion sensor (SMS0A)                              |
| xsense:strobe      | Thing  | Strobe light (SSL51)                               |
| xsense:keypad      | Thing  | Security keypad (SKP0A)                            |

## Discovery

Discovery imports the whole account tree progressively, starting automatically as soon as the `account` bridge goes online (or manually via a scan):

1. Once the `account` bridge is online, all homes of the account appear in the inbox.
1. Once a `home` thing has been added, its base stations appear.
1. Once a `station` thing has been added, its attached sensors appear.

Each inventory poll re-publishes all levels whose parent thing already exists, so after approving a thing the next level appears within seconds without a manual rescan.
Unsupported device models are logged on debug level, please report them so support can be added.

## Thing Configuration

### `account` Bridge Configuration

| Name            | Type    | Description                                                    | Default         | Required | Advanced |
|-----------------|---------|-----------------------------------------------------------------|-----------------|----------|----------|
| email           | text    | Email address of your X-Sense account                          | N/A             | yes      | no       |
| password        | text    | Password of your X-Sense account                               | N/A             | yes      | no       |
| refreshInterval | integer | Interval in seconds for polling the device inventory (min 30)  | binding default | no       | yes      |

### `home` Bridge Configuration

| Name    | Type | Description                                 | Default | Required | Advanced |
|---------|------|----------------------------------------------|---------|----------|----------|
| houseId | text | Identifier of the home in the X-Sense cloud | N/A     | yes      | no       |

### `station` Bridge Configuration

| Name      | Type | Description                       | Default | Required | Advanced |
|-----------|------|------------------------------------|---------|----------|----------|
| stationSn | text | Serial number of the base station | N/A     | yes      | no       |

### Sensor Thing Configuration

All sensor things (`smoke`, `co`, `smokeco`, `heat`, `water`, `thermohygro`, `listener`, `driveway`, `mailbox`, `door`, `motion`, `strobe`, `keypad`) share the same configuration:

| Name     | Type | Description                 | Default | Required | Advanced |
|----------|------|-------------------------------|---------|----------|----------|
| deviceSn | text | Serial number of the sensor | N/A     | yes      | no       |

### Full Example (`.things` file)

Things can be created through auto-discovery (see below) or manually via a `.things` file.
The `deviceSn`/`stationSn`/`houseId` values are taken from the X-Sense app or from a discovered thing's properties.

```java
Bridge xsense:account:myaccount "My X-Sense Account" [ email="user@example.com", password="secret" ] {
    Bridge home h1a2b3 "My Home" [ houseId="h1a2b3" ] {
        Bridge station sbs50 "Hallway Base Station" [ stationSn="SBS50-1234" ] {
            Thing smoke kitchen "Kitchen Smoke Detector" [ deviceSn="DEV567" ]
            Thing thermohygro living "Living Room Sensor" [ deviceSn="DEV890" ]
            Thing door frontdoor "Front Door Sensor" [ deviceSn="DEV123" ]
            Thing keypad entry "Entry Keypad" [ deviceSn="DEV456" ]
        }
    }
}
```

## Channels

Channels are organized in channel groups.
The groups are identical across thing types, therefore they are described once here; the table below lists which groups each thing type provides.
Live channel states are pushed by the X-Sense cloud and will be filled with the upcoming real-time (MQTT) support.

### Channel Groups

| Group    | Channel     | Type                 | Access | Description                                  |
|----------|-------------|----------------------|--------|----------------------------------------------|
| info     | path        | String               | R      | Hierarchy path as JSON (advanced, see below) |
| device   | battery     | Number               | R      | Battery level in percent                     |
| device   | lowBattery  | Switch               | R      | Low battery indication                       |
| device   | signal      | Number               | R      | RF signal strength to the base station (0-3) |
| alarm    | smoke       | Switch               | R      | Smoke alarm active                           |
| alarm    | co          | Switch               | R      | CO alarm active                              |
| alarm    | coPpm       | Number:Dimensionless | R      | Measured CO concentration                    |
| alarm    | water       | Switch               | R      | Water leak alarm active                      |
| alarm    | alarm       | Switch               | R      | Alarm active (listener, driveway, mailbox)   |
| alarm    | selfTest    | Switch               | R      | Self test in progress (advanced)             |
| alarm    | mailNotice  | Switch               | R      | New mail detected (mailbox)                  |
| control  | light       | Switch               | R/W    | Strobe light on/off                          |
| sensor   | temperature | Number:Temperature   | R      | Measured temperature                         |
| sensor   | humidity    | Number:Dimensionless | R      | Measured relative humidity                   |
| sensor   | contact     | Contact              | R      | Open/closed state (door/window sensor)       |
| sensor   | motion      | Switch               | R      | Motion detected                              |
| security | safeMode    | String               | R/W    | Station security mode (Disarmed, Home, Away) |
| security | armed       | Switch               | R      | Keypad armed state                           |

### Groups per Thing Type

| Thing Type UID     | info | device | alarm channels    | control | sensor                | security |
|--------------------|------|--------|-------------------|---------|-----------------------|----------|
| xsense:home        | yes  | -      | -                 | -       | -                     | -        |
| xsense:station     | yes  | -      | -                 | -       | -                     | safeMode |
| xsense:smoke       | yes  | yes    | smoke             | -       | -                     | -        |
| xsense:co          | yes  | yes    | co, coPpm         | -       | -                     | -        |
| xsense:smokeco     | yes  | yes    | smoke, co, coPpm  | -       | -                     | -        |
| xsense:heat        | yes  | yes    | -                 | -       | -                     | -        |
| xsense:water       | yes  | yes    | water             | -       | -                     | -        |
| xsense:thermohygro | yes  | yes    | -                 | -       | temperature, humidity | -        |
| xsense:listener    | yes  | yes    | alarm, selfTest   | -       | -                     | -        |
| xsense:driveway    | yes  | yes    | alarm             | -       | -                     | -        |
| xsense:mailbox     | yes  | yes    | alarm, mailNotice | -       | -                     | -        |
| xsense:door        | yes  | yes    | -                 | -       | contact               | -        |
| xsense:motion      | yes  | yes    | -                 | -       | motion                | -        |
| xsense:strobe      | yes  | -      | -                 | light   | -                     | -        |
| xsense:keypad      | yes  | yes    | -                 | -       | -                     | armed    |

The strobe light's `control#light` command channel is declared but not yet wired to the cloud; it becomes functional with the upcoming real-time (MQTT) support.
Mute channels are added in the next section.

### Arming and Disarming

The `station` thing provides the writable channel `security#safeMode` with three modes matching the X-Sense app:

| Mode     | Meaning                                                   |
|----------|-----------------------------------------------------------|
| Disarmed | Security sensors do not trigger the alarm                 |
| Home     | Perimeter protection (e.g. door/window sensors) is active |
| Away     | Full protection including motion sensors is active        |

Sending one of these values (case-insensitive) to the channel arms or disarms the base station via the X-Sense cloud.
The channel is not updated optimistically: the new mode is confirmed by the next inventory poll, which the binding triggers right after sending the command.
If the cloud rejects the request, a warning is logged and the previous mode remains — the command path uses the same cloud interface as the X-Sense app but is still considered experimental, so please report failures.

Example item and rule:

```java
String Station_SafeMode "Security Mode" { channel="xsense:station:myaccount:h1a2b3:sbs50:security#safeMode" }
```

```javascript
items.getItem("Station_SafeMode").sendCommand("Away");
```

### Channel Labels and Item Auto-Naming

The binding sets custom channel labels of the form `<Name>: <Function>` (e.g. "Kitchen: Smoke Alarm") using the names configured in the X-Sense app.
When linking channels via "Add Equipment to Model", openHAB therefore proposes unique item names (e.g. `Kitchen_Smoke_Alarm`) without manual editing, even when several devices provide the same channel types.

### Hierarchy Path Channel

Every `home`, `station` and sensor thing provides the advanced channel `info#path`.
Its value is a JSON string describing where the thing belongs in the account hierarchy, carrying both the stable technical id and the display name of each level.
Levels below the thing are omitted, e.g. for a sensor:

```json
{"account":"user@example.com","home":{"id":"h1a2b3","name":"My Home"},"station":{"sn":"SBS50-1234","name":"Hallway"},"device":{"sn":"DEV567","name":"Kitchen"}}
```

Usage in a JavaScript (JS Scripting) rule:

```javascript
var path = JSON.parse(items.getItem("SmokeKitchen_Path").state);
console.log("Sensor belongs to station " + path.station.name + " in home " + path.home.name);
```

Usage in a Rules DSL rule via the JSONPATH transformation:

```java
val stationName = transform("JSONPATH", "$.station.name", SmokeKitchen_Path.state.toString)
```
