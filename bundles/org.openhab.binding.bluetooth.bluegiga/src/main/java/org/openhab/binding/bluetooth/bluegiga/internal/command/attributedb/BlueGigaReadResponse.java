/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.bluetooth.bluegiga.internal.command.attributedb;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.bluetooth.bluegiga.internal.BlueGigaResponse;
import org.openhab.binding.bluetooth.bluegiga.internal.enumeration.BgApiResponse;

/**
 * Class to implement the BlueGiga command <b>read</b>.
 * <p>
 * The command reads the given attribute's value from the local database. There is a 32-byte
 * limit in the amount of data that can be read at a time. In order to read larger values multiple
 * read commands must be used with the offset properly used.
 * <p>
 * This class provides methods for processing BlueGiga API commands.
 * <p>
 * Note that this code is autogenerated. Manual changes may be overwritten.
 *
 * @author Chris Jackson - Initial contribution of Java code generator
 */
@NonNullByDefault
public class BlueGigaReadResponse extends BlueGigaResponse {
    public static int COMMAND_CLASS = 0x02;
    public static int COMMAND_METHOD = 0x01;

    /**
     * Handle of the attribute which was read
     * <p>
     * BlueGiga API type is <i>uint16</i> - Java type is {@link int}
     */
    private int handle;

    /**
     * Offset read from
     * <p>
     * BlueGiga API type is <i>uint16</i> - Java type is {@link int}
     */
    private int offset;

    /**
     * 0 : the command was successful. Otherwise an error occurred
     * <p>
     * BlueGiga API type is <i>BgApiResponse</i> - Java type is {@link BgApiResponse}
     */
    private BgApiResponse result;

    /**
     * Value of the attribute
     * <p>
     * BlueGiga API type is <i>uint8array</i> - Java type is {@link int[]}
     */
    private int[] value;

    /**
     * Response constructor
     */
    public BlueGigaReadResponse(int[] inputBuffer) {
        // Super creates deserializer and reads header fields
        super(inputBuffer);

        event = (inputBuffer[0] & 0x80) != 0;

        // Deserialize the fields
        handle = deserializeUInt16();
        offset = deserializeUInt16();
        result = deserializeBgApiResponse();
        value = deserializeUInt8Array();
    }

    /**
     * Handle of the attribute which was read
     * <p>
     * BlueGiga API type is <i>uint16</i> - Java type is {@link int}
     *
     * @return the current handle as {@link int}
     */
    public int getHandle() {
        return handle;
    }

    /**
     * Offset read from
     * <p>
     * BlueGiga API type is <i>uint16</i> - Java type is {@link int}
     *
     * @return the current offset as {@link int}
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 0 : the command was successful. Otherwise an error occurred
     * <p>
     * BlueGiga API type is <i>BgApiResponse</i> - Java type is {@link BgApiResponse}
     *
     * @return the current result as {@link BgApiResponse}
     */
    public BgApiResponse getResult() {
        return result;
    }

    /**
     * Value of the attribute
     * <p>
     * BlueGiga API type is <i>uint8array</i> - Java type is {@link int[]}
     *
     * @return the current value as {@link int[]}
     */
    public int[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("BlueGigaReadResponse [handle=");
        builder.append(handle);
        builder.append(", offset=");
        builder.append(offset);
        builder.append(", result=");
        builder.append(result);
        builder.append(", value=");
        for (int c = 0; c < value.length; c++) {
            if (c > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", value[c]));
        }
        builder.append(']');
        return builder.toString();
    }
}