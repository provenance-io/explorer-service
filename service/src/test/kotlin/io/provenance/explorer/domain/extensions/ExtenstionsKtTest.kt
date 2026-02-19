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
    fun `test safeDecodeBase64ToText with various cases`() {
        val base64String = "SGVsbG8gd29ybGQ="
        val expectedDecodedString = "Hello world"
        assertEquals(expectedDecodedString, base64String.safeDecodeBase64ToText(), "Valid Base64 string should decode to 'Hello world'")

        val invalidBase64String = "Hello world"
        val expectedInvalidOutput = invalidBase64String
        assertEquals(expectedInvalidOutput, invalidBase64String.safeDecodeBase64ToText(), "Invalid Base64 string should return the original string")

        val emptyString = ""
        val expectedEmptyOutput = emptyString
        assertEquals(expectedEmptyOutput, emptyString.safeDecodeBase64ToText(), "Empty string should return the original string")

        val malformedBase64String = "SGVsbG8gd29ybGQ"
        val expectedMalformedOutput = malformedBase64String
        assertEquals(expectedMalformedOutput, malformedBase64String.safeDecodeBase64ToText(), "Malformed Base64 string should return the original string")
    }

    @Test
    fun `test safeDecodeBase64ToText handles binary data correctly`() {
        // Test 1: Valid base64-encoded text should decode correctly
        val base64Text = "cmVjZWl2ZXI=" // base64 of "receiver"
        val decodedText = base64Text.safeDecodeBase64ToText()
        assertEquals("receiver", decodedText, "Valid base64-encoded text should decode correctly")

        // Test 2: Binary data (signature) should remain as base64
        val base64Signature = "7fdg7Gf9At3ICCzOABRqLCCmPLVktdVzV1KMLTa6ZXACwMesljgKAj2C5Qex614pibFmqMNaIdVUQCI5CNwqiQ=="
        val signatureResult = base64Signature.safeDecodeBase64ToText()
        assertEquals(base64Signature, signatureResult, "Binary data (signatures) should remain as base64")

        // Test 3: acc_seq field (text) should decode correctly
        val base64AccSeq = "cGIxbGE4Z3R0eDJscHI2YWhhcmZnZDJkeDc4cHR6d3Blbmh1cDhxeDgvMjk0ODUx" // base64 of "pb1la8gttx2lpr6aharfgd2dx78ptzwpenhup8qx8/294851"
        val accSeqResult = base64AccSeq.safeDecodeBase64ToText()
        assertEquals("pb1la8gttx2lpr6aharfgd2dx78ptzwpenhup8qx8/294851", accSeqResult, "acc_seq should decode to valid text")

        // Test 4: module field (text) should decode correctly
        val base64Module = "ZXhjaGFuZ2U=" // base64 of "exchange"
        val moduleResult = base64Module.safeDecodeBase64ToText()
        assertEquals("exchange", moduleResult, "module should decode to valid text")

        // Test 5: Non-base64 string should be returned as-is
        val plainText = "receiver"
        val plainTextResult = plainText.safeDecodeBase64ToText()
        assertEquals("receiver", plainTextResult, "Non-base64 strings should be returned as-is")

        // Test 6: Empty string should return empty string
        val emptyResult = "".safeDecodeBase64ToText()
        assertEquals("", emptyResult, "Empty string should return empty string")

        // Test 7: Binary data with invalid control characters should remain as base64
        // Create a base64 string that decodes to binary bytes (not valid UTF-8)
        val binaryBytes = byteArrayOf(0x1E, 0x4D, 0x4D, 0x27, 0x7E, 0x0A) // Similar to corrupted "4M4M'~\n"
        val base64Binary = java.util.Base64.getEncoder().encodeToString(binaryBytes)
        val binaryResult = base64Binary.safeDecodeBase64ToText()
        // Should keep as base64 since it contains invalid control characters
        assertEquals(base64Binary, binaryResult, "Binary data with invalid control chars should remain as base64")

        // Test 8: Coin amount string that looks like base64 but decodes to binary should remain as-is
        val coinAmount = "78000000000nfgrd"
        val coinAmountResult = coinAmount.safeDecodeBase64ToText()
        assertEquals(coinAmount, coinAmountResult, "Coin amount string that looks like base64 but decodes to binary should remain as original")
    }
}
