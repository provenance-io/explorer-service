package io.provenance.explorer.domain.extensions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionsKtTest {
    @Test
    fun `test toObjectNode properly converts json strings to objecs`() {
        val inputJsonWithJsonStrObj = "{\"amount\":\"1\",\"denom\":\"psa.3zlqy2ecncvalbycokxnoh.stock\",\"memo\":\"{\\\"marker\\\":{\\\"transfer-auths\\\":[\\\"tp19zf8q9swrsspkdljumwh04zjac4nkfvju6ehl9\\\",\\\"tp1tk6fqws0su7fzp090edrauaa756mdyjfdw0507\\\",\\\"tp1a53udazy8ayufvy0s434pfwjcedzqv34vfvvyc\\\"],\\\"allow-force-transfer\\\":false}}\",\"receiver\":\"tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3\",\"sender\":\"tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3\"}"
        val inputFormatedJsonWithJsonStrObj = "{\n" +
            "  \"amount\": \"1\",\n" +
            "  \"denom\": \"psa.3zlqy2ecncvalbycokxnoh.stock\",\n" +
            "  \"memo\": \"{\\\"marker\\\":{\\\"transfer-auths\\\":[\\\"tp19zf8q9swrsspkdljumwh04zjac4nkfvju6ehl9\\\",\\\"tp1tk6fqws0su7fzp090edrauaa756mdyjfdw0507\\\",\\\"tp1a53udazy8ayufvy0s434pfwjcedzqv34vfvvyc\\\"],\\\"allow-force-transfer\\\":false}}\",\n" +
            "  \"receiver\": \"tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3\",\n" +
            "  \"sender\": \"tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3\"\n" +
            "}"

        val tests = mapOf(
            "test input with string that has json string as value for memo" to inputJsonWithJsonStrObj,
            "test input with formatted json and json string as value for memo" to inputFormatedJsonWithJsonStrObj
        )

        for ((testname, json) in tests) {
            val actualJsonObj = json.toObjectNode()
            assertEquals("tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3", actualJsonObj.get("receiver").asText(), testname)
            assertEquals("tp12wyy028sd3yf3j0z950fq5p3zvzgpzgds3dqp3", actualJsonObj.get("sender").asText(), testname)
            assertEquals("1", actualJsonObj.get("amount").asText(), testname)
            assertEquals("psa.3zlqy2ecncvalbycokxnoh.stock", actualJsonObj.get("denom").asText(), testname)
            assertEquals("{\"marker\":{\"transfer-auths\":[\"tp19zf8q9swrsspkdljumwh04zjac4nkfvju6ehl9\",\"tp1tk6fqws0su7fzp090edrauaa756mdyjfdw0507\",\"tp1a53udazy8ayufvy0s434pfwjcedzqv34vfvvyc\"],\"allow-force-transfer\":false}}", actualJsonObj.get("memo").asText(), testname)
        }
    }

    @Test
    fun `test tryFromBase64 with various cases`() {
        val base64String = "SGVsbG8gd29ybGQ="
        val expectedDecodedString = "Hello world"
        assertEquals(expectedDecodedString, base64String.tryFromBase64(), "Valid Base64 string should decode to 'Hello world'")

        val invalidBase64String = "Hello world"
        val expectedInvalidOutput = invalidBase64String
        assertEquals(expectedInvalidOutput, invalidBase64String.tryFromBase64(), "Invalid Base64 string should return the original string")

        val emptyString = ""
        val expectedEmptyOutput = emptyString
        assertEquals(expectedEmptyOutput, emptyString.tryFromBase64(), "Empty string should return the original string")

        val malformedBase64String = "SGVsbG8gd29ybGQ" // Missing padding character '='
        val expectedMalformedOutput = malformedBase64String
        assertEquals(expectedMalformedOutput, malformedBase64String.tryFromBase64(), "Malformed Base64 string should return the original string")
    }
}
