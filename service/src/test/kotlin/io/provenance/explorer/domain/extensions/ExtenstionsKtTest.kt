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
}
