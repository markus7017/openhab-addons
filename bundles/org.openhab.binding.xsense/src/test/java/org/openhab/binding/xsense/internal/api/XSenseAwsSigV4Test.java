/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.xsense.internal.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/*
 * Vectors are taken verbatim from the official AWS Signature Version 4 test suite (get-vanilla,
 * post-vanilla, get-vanilla-query-order-key-case): access key AKIDEXAMPLE, secret key
 * wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY, region us-east-1, service "service", signing time
 * 20150830T123600Z.
 */
@NonNullByDefault
public class XSenseAwsSigV4Test {

    private static final String ACCESS_KEY = "AKIDEXAMPLE";
    private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
    private static final Instant SIGNING_TIME = Instant.parse("2015-08-30T12:36:00Z");

    private final XSenseAwsSigV4 signer = new XSenseAwsSigV4("us-east-1", "service");

    @Test
    public void getVanillaMatchesOfficialTestVector() throws Exception {
        Map<String, String> headers = signer.sign("GET", URI.create("https://example.amazonaws.com/"), Map.of(), "",
                ACCESS_KEY, SECRET_KEY, SIGNING_TIME);

        assertEquals("example.amazonaws.com", headers.get("Host"));
        assertEquals("20150830T123600Z", headers.get("X-Amz-Date"));
        assertEquals(
                "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=5fa00fa31553b73ebf1942676e86291e8372ff2a2260956d9b8aae1d763fbf31",
                headers.get("Authorization"));
    }

    @Test
    public void postVanillaMatchesOfficialTestVector() throws Exception {
        Map<String, String> headers = signer.sign("POST", URI.create("https://example.amazonaws.com/"), Map.of(), "",
                ACCESS_KEY, SECRET_KEY, SIGNING_TIME);

        assertEquals(
                "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=5da7c1a2acd57cee7505fc6676e4e544621c30862966e37dddb68e92efbe5d6b",
                headers.get("Authorization"));
    }

    @Test
    public void queryParametersAreSortedMatchingOfficialTestVector() throws Exception {
        // get-vanilla-query-order-key-case: parameters given in reverse order must be sorted
        Map<String, String> headers = signer.sign("GET",
                URI.create("https://example.amazonaws.com/?Param2=value2&Param1=value1"), Map.of(), "", ACCESS_KEY,
                SECRET_KEY, SIGNING_TIME);

        assertEquals(
                "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150830/us-east-1/service/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, "
                        + "Signature=b97d918cfa904a5beff61c982a1b6f458b799221646efd99d3219ec94cdf2500",
                headers.get("Authorization"));
    }

    @Test
    public void extraHeadersAreSignedAlphabetically() throws Exception {
        Map<String, String> headers = signer.sign("POST", URI.create("https://example.amazonaws.com/"),
                Map.of("Content-Type", "application/x-amz-json-1.0", "X-Amz-Security-Token", "token123"), "{}",
                ACCESS_KEY, SECRET_KEY, SIGNING_TIME);

        String authorization = headers.get("Authorization");
        assertNotNull(authorization);
        assertTrue(authorization.contains("SignedHeaders=content-type;host;x-amz-date;x-amz-security-token,"));
    }

    @Test
    public void canonicalQueryStringSortsAndEncodes() {
        assertEquals("", XSenseAwsSigV4.canonicalQueryString(null));
        assertEquals("", XSenseAwsSigV4.canonicalQueryString(""));
        assertEquals("Param1=value1&Param2=value2", XSenseAwsSigV4.canonicalQueryString("Param2=value2&Param1=value1"));
        assertEquals("name=2nd_appmode", XSenseAwsSigV4.canonicalQueryString("name=2nd_appmode"));
        assertEquals("flag=", XSenseAwsSigV4.canonicalQueryString("flag"));
    }

    @Test
    public void stringToSignMatchesOfficialTestVector() throws Exception {
        String canonicalRequest = """
                GET
                /

                host:example.amazonaws.com
                x-amz-date:20150830T123600Z

                host;x-amz-date
                e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855""";
        assertEquals("""
                AWS4-HMAC-SHA256
                20150830T123600Z
                20150830/us-east-1/service/aws4_request
                bb579772317eb040ac9ed261061d46c1f17a8133879d6129b6e1c25292927e63""", XSenseAwsSigV4
                .stringToSign("20150830T123600Z", "20150830/us-east-1/service/aws4_request", canonicalRequest));
    }

    @Test
    public void uriEncodeKeepsUnreservedAndEncodesTheRest() {
        assertEquals("AZaz09-_.~", XSenseAwsSigV4.uriEncode("AZaz09-_.~"));
        assertEquals("a%20b%2Fc%3D1%26x", XSenseAwsSigV4.uriEncode("a b/c=1&x"));
        assertEquals("%E2%82%AC", XSenseAwsSigV4.uriEncode("€"));
    }

    @Test
    public void hexSha256OfEmptyStringIsWellKnownHash() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", XSenseAwsSigV4.hexSha256(""));
    }
}
