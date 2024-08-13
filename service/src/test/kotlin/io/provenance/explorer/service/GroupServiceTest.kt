package io.provenance.explorer.service

import com.google.protobuf.util.JsonFormat
import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.explorer.domain.models.explorer.TxData
import io.provenance.explorer.domain.models.explorer.TxUpdate
import io.provenance.explorer.grpc.v1.GroupGrpcClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class GroupServiceTest {
    private lateinit var groupService: GroupService
    private lateinit var groupClient: GroupGrpcClient
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        groupClient = mock(GroupGrpcClient::class.java)
        accountService = mock(AccountService::class.java)
        groupService = GroupService(groupClient, accountService)
    }

    @Test
    fun testSaveGroups() {

        val txResponseBuilder = ServiceOuterClass.GetTxsEventResponse.newBuilder()
        JsonFormat.parser().merge(jsonResponse, txResponseBuilder)
        val txResponse = txResponseBuilder.build()

//        val txData = TxData()
//        val txUpdate = TxUpdate()
//
//        groupService.saveGroups(txResponse, txData, txUpdate)

    }

    var jsonResponse= """ 
        {
            "txs": [
              {
                "hash": "E2B1E075CE256344E7FBD8F03D6047F6106E9DE8E6072644D481D36E54C55300",
                "height": "18232283",
                "index": 0,
                "tx_result": {
                  "code": 0,
                  "data": "EuYCCi8vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVNlc3Npb25SZXNwb25zZRKyAgqvAgohASBallZSY06XmCzQa/72OU3+gdyKBwBJpK5KzajAkvb4EgEBGhAgWpZWUmNOl5gs0Gv+9jlNIhD+gdyKBwBJpK5KzajAkvb4KkNzZXNzaW9uMXF5czk0OWprMmYzNWE5dWM5bmd4aGxoazg5eGxhcXd1M2dyc3FqZHk0ZTl2bTJ4cWp0bTBzeDVnNGh4MiRmZTgxZGM4YS0wNzAwLTQ5YTQtYWU0YS1jZGE4YzA5MmY2Zjg6eAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0YtXEsqeYd7TQZO2qZNUbnEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAYtXEsqeYd7TQZO2qZNUbnKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4MzNkdDM5ajU3djgwZHhzdm5rNjVleDRyd3c1cXloOGMyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0lFOFHWt3/s3j9sH6aEJIXEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAlFOFHWt3/s3j9sH6aEJIXKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4ajI5OHBnYWRkbWxhbjByN21xbDU2enpmcHd4ZTdoZXMyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU31pGdcfgZMfwKtWsnluZA2EgECGhAgWpZWUmNOl5gs0Gv+9jlNIhD1pGdcfgZMfwKtWsnluZA2KkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4bHRmcjh0M2xxdm5ybHEyazQ0ajA5aHhncnZycHpydDgyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU2iP7kl58gV+QbZEyXO4hrgEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhCiP7kl58gV+QbZEyXO4hrgKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4NnkwYWV5aG51czkwZXFtdjN4Znd3dWdkd3Fqc3FodnkyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU3RLKpM0q+GMWHVFlhUHvq7EgECGhAgWpZWUmNOl5gs0Gv+9jlNIhDRLKpM0q+GMWHVFlhUHvq7KkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4YXp0OTJmbmYybHAzM3Y4MjN2a3o1cm1hdGt1bm56a3oyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0mFRdpfD/NMditGx3monlmEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAmFRdpfD/NMditGx3monlmKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4anY5Z2hkOTdybG5mM216azNrODB4NWZ1a3Zjbm10c2MyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0lMnI2AUBOq9K+3y+FbJgKEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAlMnI2AUBOq9K+3y+FbJgKKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4ajJ2bmp4Y3E1cW40dDYybGQ3dHU5ZGp2cTVkd2twcXQyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU2bFrRNDgpcrE+Wi++wtbXrEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhCbFrRNDgpcrE+Wi++wtbXrKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4ZWs5NDVmNThxNWg5dmY3dGdobWFza2s2N2s0cmg5d20yeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0xzau82iCJ4kjicme7pjhNEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAxzau82iCJ4kjicme7pjhNKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4bnJuZHRobmR6cHowemZyMzh5ZWFtNWN1eTZkc2M2cHUyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU12ak/1TUVsejjmz+M67j+jEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhB2ak/1TUVsejjmz+M67j+jKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4aHY2ajA3NHg1Mm1yNjhybnZsY2U2YWNsNnh4eHUyOWYyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU1HK78Ukj4ufO/YUpglxAHoEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhBHK78Ukj4ufO/YUpglxAHoKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4NXcyYWx6amZydXRudWFsdjk5eHA5Y3NxN3NjZ3E3N3cyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0faThm191ZAf+PfTXIADNpEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAfaThm191ZAf+PfTXIADNpKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4Mzc2ZmN2bXRhNmtncGw3OGg2ZHdncXFla2pyZWV1eWQyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU2tuSVkVINf+L7Zp3AaU9sIEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhCtuSVkVINf+L7Zp3AaU9sIKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4Nm13Zjl2MzJneGhsY2htdjZ3dXE2MjBkc3N0eGNtdWoyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU0G6+MUPPUJtSXeqFA1Xh20EgECGhAgWpZWUmNOl5gs0Gv+9jlNIhAG6+MUPPUJtSXeqFA1Xh20KkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4c2Q2bHJ6czcwMnpkNHloMDJzNXA0dGN3bWdtd2dkc2EyeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZBK+AgouL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXNwb25zZRKLAgqIAgohAiBallZSY06XmCzQa/72OU2+Z3+bfjSZO2su2YJWEwqzEgECGhAgWpZWUmNOl5gs0Gv+9jlNIhC+Z3+bfjSZO2su2YJWEwqzKkJyZWNvcmQxcWdzOTQ5amsyZjM1YTl1YzluZ3hobGhrODl4bXVlbWxuZGxyZnhmbWR2aGRucWprenY5dHg1d2UwM20yeAoRACBallZSY06XmCzQa/72OU0SAQAaECBallZSY06XmCzQa/72OU0iKHNjb3BlMXFxczk0OWprMmYzNWE5dWM5bmd4aGxoazg5eHNoc3NtdDYqJDIwNWE5NjU2LTUyNjMtNGU5Ny05ODJjLWQwNmJmZWY2Mzk0ZA==",
                  "log": "",
                  "info": "",
                  "gas_wanted": "1033725",
                  "gas_used": "693996",
                  "events": [
                    {
                      "type": "coin_spent",
                      "attributes": [
                        {
                          "key": "spender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1969246125nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_received",
                      "attributes": [
                        {
                          "key": "receiver",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1969246125nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "transfer",
                      "attributes": [
                        {
                          "key": "recipient",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1969246125nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "fee",
                          "value": "9846230625nhash",
                          "index": true
                        },
                        {
                          "key": "fee_payer",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "min_fee_charged",
                          "value": "1969246125nhash",
                          "index": true
                        },
                        {
                          "key": "fee_payer",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "acc_seq",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd/5461635",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "signature",
                          "value": "GP6Mhj23IjBySRzXTE5NlfmiD4iyq+IwCmg7mUPWlncDRxPlWyFL7IUYnGEOaS49wC9tKWtPZLIZHSUPSrZTQw==",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteSessionRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventSessionCreated",
                      "attributes": [
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteSession\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "1",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89x33dt39j57v80dxsvnk65ex4rww5qyh8c\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "1",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "1",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "2",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xj298pgaddmlan0r7mql56zzfpwxe7hes\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "2",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "2",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "3",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xltfr8t3lqvnrlq2k44j09hxgrvrpzrt8\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "3",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "3",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "4",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89x6y0aeyhnus90eqmv3xfwwugdwqjsqhvy\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "4",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "4",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "5",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xazt92fnf2lp33v823vkz5rmatkunnzkz\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "5",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "5",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "6",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xjv9ghd97rlnf3mzk3k80x5fukvcnmtsc\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "6",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "6",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "7",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xj2vnjxcq5qn4t62ld7tu9djvq5dwkpqt\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "7",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "7",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "8",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xek945f58q5h9vf7tghmaskk67k4rh9wm\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "8",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "8",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "9",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xnrndthndzpz0zfr38yeam5cuy6dsc6pu\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "9",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "9",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "10",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xhv6j074x52mr68rnvlce6acl6xxxu29f\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "10",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "10",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "11",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89x5w2alzjfrutnualv99xp9csq7scgq77w\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "11",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "11",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "12",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89x376fcvmta6kgpl78h6dwgqqekjreeuyd\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "12",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "12",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "13",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89x6mwf9v32gxhlchmv6wuq620dsstxcmuj\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "13",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "13",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "14",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xsd6lrzs702zd4yh02s5p4tcwmgmwgdsa\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "14",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "14",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/provenance.metadata.v1.MsgWriteRecordRequest",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "15",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventRecordUpdated",
                      "attributes": [
                        {
                          "key": "record_addr",
                          "value": "\"record1qgs949jk2f35a9uc9ngxhlhk89xmuemlndlrfxfmdvhdnqjkzv9tx5we03m\"",
                          "index": true
                        },
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89xlaqwu3grsqjdy4e9vm2xqjtm0sx5g4hx\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "15",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventSessionDeleted",
                      "attributes": [
                        {
                          "key": "scope_addr",
                          "value": "\"scope1qqs949jk2f35a9uc9ngxhlhk89xshssmt6\"",
                          "index": true
                        },
                        {
                          "key": "session_addr",
                          "value": "\"session1qys949jk2f35a9uc9ngxhlhk89x58elqy828w39gsnefpkgxhqemkukffl2\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "15",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "provenance.metadata.v1.EventTxCompleted",
                      "attributes": [
                        {
                          "key": "endpoint",
                          "value": "\"WriteRecord\"",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "\"metadata\"",
                          "index": true
                        },
                        {
                          "key": "signers",
                          "value": "[\"pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd\"]",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "15",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_spent",
                      "attributes": [
                        {
                          "key": "spender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "7876984500nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_received",
                      "attributes": [
                        {
                          "key": "receiver",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "7876984500nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "transfer",
                      "attributes": [
                        {
                          "key": "recipient",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "7876984500nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "sender",
                          "value": "pb1xvd4k9jg5h0d4dhzr4z0txtwe9p5zxf58xcmxd",
                          "index": true
                        }
                      ]
                    }
                  ],
                  "codespace": ""
                },
                "tx": "Cs86CoYCCi4vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVNlc3Npb25SZXF1ZXN0EtMBCqUBCiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgSEQODOgijh3ldCPjtc//vqQ+yGi0KKXBiMXh2ZDRrOWpnNWgwZDRkaHpyNHowdHh0d2U5cDV6eGY1OHhjbXhkEAEiPmNvbS5maWd1cmUubG9zLmNvbnRyYWN0Lm9yaWdpbmF0aW9uLlVwZGF0ZVByb3BlcnR5TG9hbkNvbnRyYWN0EilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZArvAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0Er0DCuACChJibG9ja2NoYWluX2N1c3RvZHkSIQEgWpZWUmNOl5gs0Gv+9jlN/oHcigcASaSuSs2owJL2+Bp4EixIZGpzV1R4TUJNSWxxYlM5T0d6akZ4d2w3enlTUTJLeUsyRlVtVy91K1pVPRo1aW8ucHJvdmVuYW5jZS5wcm90by5sb2FuLkxvYW5Qcm90b3MkQmxvY2tjaGFpbkN1c3RvZHkiEWJsb2NrY2hhaW5DdXN0b2R5InsKEmJsb2NrY2hhaW5fY3VzdG9keRosK2RreXVCdGZpc2hqS0lxNFZYME82Tk9mVDlZb2l6UDAyZTVnTFdqVjN2QT0iNWlvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJEJsb2NrY2hhaW5DdXN0b2R5KAEqMAosK2RreXVCdGZpc2hqS0lxNFZYME82Tk9mVDlZb2l6UDAyZTVnTFdqVjN2QT0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQq7AwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0EokDCqwCCgdmdW5kaW5nEiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgaZBIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aK2lvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJEZ1bmRpbmciB2Z1bmRpbmciZgoHZnVuZGluZxosNDdERVFwajhIQlNhKy9USW1XKzVKQ2V1UWVSa201Tk1wSldaRzNoU3VGVT0iK2lvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJEZ1bmRpbmcoASowCixQcGFCVjJvRGlJSG0zdnNwWHVrWkdMOVg5eTRzbTlGdTNFUzdYbjNsVzBRPRABEilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZCotCilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZBABCs8DCi0vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVJlY29yZFJlcXVlc3QSnQMKwAIKCXNlcnZpY2luZxIhASBallZSY06XmCzQa/72OU3+gdyKBwBJpK5KzajAkvb4Gm0SLEhkanNXVHhNQk1JbHFiUzlPR3pqRnh3bDd6eVNRMkt5SzJGVW1XL3UrWlU9GjJpby5wcm92ZW5hbmNlLnByb3RvLmFzc2V0LkxvYW5Qcm90b3MkTG9hblNlcnZpY2luZyIJc2VydmljaW5nIm8KCXNlcnZpY2luZxoscE40S3ZSNlhUd0YrcmEwTkp2Q1VxN3dERk5PcjNGZTE5bVk2WnJRV2hVTT0iMmlvLnByb3ZlbmFuY2UucHJvdG8uYXNzZXQuTG9hblByb3RvcyRMb2FuU2VydmljaW5nKAEqMAoscE40S3ZSNlhUd0YrcmEwTkp2Q1VxN3dERk5PcjNGZTE5bVk2WnJRV2hVTT0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrjAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0ErEDCtQCChJhZGRpdGlvbmFsX3BhcnRpZXMSIQEgWpZWUmNOl5gs0Gv+9jlN/oHcigcASaSuSs2owJL2+BpyEixIZGpzV1R4TUJNSWxxYlM5T0d6akZ4d2w3enlTUTJLeUsyRlVtVy91K1pVPRovaW8ucHJvdmVuYW5jZS5wcm90by5sb2FuLkxvYW5Qcm90b3MkUGFydGllc0xpc3QiEWFkZGl0aW9uYWxQYXJ0aWVzInUKEmFkZGl0aW9uYWxfcGFydGllcxosNDdERVFwajhIQlNhKy9USW1XKzVKQ2V1UWVSa201Tk1wSldaRzNoU3VGVT0iL2lvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJFBhcnRpZXNMaXN0KAEqMAosNDdERVFwajhIQlNhKy9USW1XKzVKQ2V1UWVSa201Tk1wSldaRzNoU3VGVT0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQruAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0ErwDCt8CChRjcmVkaXRfcmVwb3J0c19jbGVhbhIhASBallZSY06XmCzQa/72OU3+gdyKBwBJpK5KzajAkvb4GnkSLEhkanNXVHhNQk1JbHFiUzlPR3pqRnh3bDd6eVNRMkt5SzJGVW1XL3UrWlU9GjVpby5wcm92ZW5hbmNlLnByb3RvLmxvYW4uTG9hblByb3RvcyRDcmVkaXRSZXBvcnRzTGlzdCISY2xlYW5DcmVkaXRSZXBvcnRzIncKDmNyZWRpdF9yZXBvcnRzGiw1VnEwcUlIaGdtQlJQVVBhRkRXR3hlYU5XSGNZQ3ZxeXN2elBFSXN6d1FZPSI1aW8ucHJvdmVuYW5jZS5wcm90by5sb2FuLkxvYW5Qcm90b3MkQ3JlZGl0UmVwb3J0c0xpc3QoASowCiw1VnEwcUlIaGdtQlJQVVBhRkRXR3hlYU5XSGNZQ3ZxeXN2elBFSXN6d1FZPRABEilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZCotCilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZBABCuMDCi0vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVJlY29yZFJlcXVlc3QSsQMK1AIKDmNyZWRpdF9yZXBvcnRzEiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgadBIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aNWlvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJENyZWRpdFJlcG9ydHNMaXN0Ig1jcmVkaXRSZXBvcnRzIncKDmNyZWRpdF9yZXBvcnRzGiw1VnEwcUlIaGdtQlJQVVBhRkRXR3hlYU5XSGNZQ3ZxeXN2elBFSXN6d1FZPSI1aW8ucHJvdmVuYW5jZS5wcm90by5sb2FuLkxvYW5Qcm90b3MkQ3JlZGl0UmVwb3J0c0xpc3QoASowCiw1VnEwcUlIaGdtQlJQVVBhRkRXR3hlYU5XSGNZQ3ZxeXN2elBFSXN6d1FZPRABEilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZCotCilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZBABCpcECi0vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVJlY29yZFJlcXVlc3QS5QMKiAMKGWRpZ2l0YWxfc2lnbmF0dXJlX3BhY2tldHMSIQEgWpZWUmNOl5gs0Gv+9jlN/oHcigcASaSuSs2owJL2+BqHARIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aPmlvLnByb3ZlbmFuY2UucHJvdG8uY29tbW9uLkRvY3VtZW50UHJvdG9zJERvY3VtZW50V2l0aERhdGFMaXN0IhdkaWdpdGFsU2lnbmF0dXJlUGFja2V0cyKLAQoZZGlnaXRhbF9zaWduYXR1cmVfcGFja2V0cxosVmMzaURuMkxadFRoU0d4OVpBdFBNUCtJeHpSdU9zZmdVblVJNjJtZnhCdz0iPmlvLnByb3ZlbmFuY2UucHJvdG8uY29tbW9uLkRvY3VtZW50UHJvdG9zJERvY3VtZW50V2l0aERhdGFMaXN0KAEqMAosVmMzaURuMkxadFRoU0d4OVpBdFBNUCtJeHpSdU9zZmdVblVJNjJtZnhCdz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrXAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0EqUDCsgCCglkb2N1bWVudHMSIQEgWpZWUmNOl5gs0Gv+9jlN/oHcigcASaSuSs2owJL2+BpxEixIZGpzV1R4TUJNSWxxYlM5T0d6akZ4d2w3enlTUTJLeUsyRlVtVy91K1pVPRo2aW8ucHJvdmVuYW5jZS5wcm90by5jb21tb24uRG9jdW1lbnRQcm90b3MkRG9jdW1lbnRMaXN0Iglkb2N1bWVudHMicwoJZG9jdW1lbnRzGixuTU1YQy85RWNFcWhuNktUM2FwNGhmdXp5WWEzK1NHYnp3dWVlcnZ0emp3PSI2aW8ucHJvdmVuYW5jZS5wcm90by5jb21tb24uRG9jdW1lbnRQcm90b3MkRG9jdW1lbnRMaXN0KAEqMAosbk1NWEMvOUVjRXFobjZLVDNhcDRoZnV6eVlhMytTR2J6d3VlZXJ2dHpqdz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrjAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0ErEDCtQCCg5pbmNvbWVfcmVjb3JkcxIhASBallZSY06XmCzQa/72OU3+gdyKBwBJpK5KzajAkvb4GnQSLEhkanNXVHhNQk1JbHFiUzlPR3pqRnh3bDd6eVNRMkt5SzJGVW1XL3UrWlU9GjVpby5wcm92ZW5hbmNlLnByb3RvLmxvYW4uTG9hblByb3RvcyRJbmNvbWVSZWNvcmRzTGlzdCINaW5jb21lUmVjb3JkcyJ3Cg5pbmNvbWVfcmVjb3JkcxosNTR3V0F0SHVuQjRKWnBHRTVIRUo1Sll4dGpEbThSb0gzT2gwbWorcnZZZz0iNWlvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJEluY29tZVJlY29yZHNMaXN0KAEqMAosNTR3V0F0SHVuQjRKWnBHRTVIRUo1Sll4dGpEbThSb0gzT2gwbWorcnZZZz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrUAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0EqIDCsUCCg1saWVuX3Byb3BlcnR5EiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgabRIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aL2lvLnByb3ZlbmFuY2UucHJvdG8uUHJvcGVydHlQcm90b3MkTGllblByb3BlcnR5IgxsaWVuUHJvcGVydHkicAoNbGllbl9wcm9wZXJ0eRosblRqVWlQL0dJWW8razhzb01OQmY1ZlNTMVpSdE5iNWpHSGNKTElDQVBSbz0iL2lvLnByb3ZlbmFuY2UucHJvdG8uUHJvcGVydHlQcm90b3MkTGllblByb3BlcnR5KAEqMAosblRqVWlQL0dJWW8razhzb01OQmY1ZlNTMVpSdE5iNWpHSGNKTElDQVBSbz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQqsAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0EvoCCp0CCgRsb2FuEiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgaXhIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aKGlvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJExvYW4iBGxvYW4iYAoEbG9hbhosak93L3YyaDd5dERzVmVzeHllVkczblRFZXRyUHBCd0h3R0dvMzlCSE1pYz0iKGlvLnByb3ZlbmFuY2UucHJvdG8ubG9hbi5Mb2FuUHJvdG9zJExvYW4oASowCixqT3cvdjJoN3l0RHNWZXN4eWVWRzNuVEVldHJQcEJ3SHdHR28zOUJITWljPRABEilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZCotCilwYjF4dmQ0azlqZzVoMGQ0ZGh6cjR6MHR4dHdlOXA1enhmNTh4Y214ZBABCsYDCi0vcHJvdmVuYW5jZS5tZXRhZGF0YS52MS5Nc2dXcml0ZVJlY29yZFJlcXVlc3QSlAMKtwIKDXByaW1hcnlfcGFydHkSIQEgWpZWUmNOl5gs0Gv+9jlN/oHcigcASaSuSs2owJL2+BpmEixIZGpzV1R4TUJNSWxxYlM5T0d6akZ4d2w3enlTUTJLeUsyRlVtVy91K1pVPRooaW8ucHJvdmVuYW5jZS5wcm90by5DdXN0b21lclByb3RvcyRQYXJ0eSIMcHJpbWFyeVBhcnR5ImkKDXByaW1hcnlfcGFydHkaLGx0YXlOUlJnVFdJZFRYaEM4citld0hYOVZYbytrZEhoZ2JYY0JWcXZ2MG89Iihpby5wcm92ZW5hbmNlLnByb3RvLkN1c3RvbWVyUHJvdG9zJFBhcnR5KAEqMAosbHRheU5SUmdUV0lkVFhoQzhyK2V3SFg5VlhvK2tkSGhnYlhjQlZxdnYwbz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrmAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0ErQDCtcCChBzaWduZWRfcHJvbV9ub3RlEiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgadBIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aNGlvLnByb3ZlbmFuY2UucHJvdG8uY29tbW9uLkRvY3VtZW50UHJvdG9zJERpc2Nsb3N1cmUiDnNpZ25lZFByb21Ob3RlIngKEHNpZ25lZF9wcm9tX25vdGUaLDNPakpsejgyMjhjZFhjaVNMc0pDNUU4Uitpc2E3OE9xWnN1UTBTcHFxYTA9IjRpby5wcm92ZW5hbmNlLnByb3RvLmNvbW1vbi5Eb2N1bWVudFByb3RvcyREaXNjbG9zdXJlKAEqMAosM09qSmx6ODIyOGNkWGNpU0xzSkM1RThSK2lzYTc4T3Fac3VRMFNwcXFhMD0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQrvAwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0Er0DCuACChF0cmlfbWVyZ2VfcmVwb3J0cxIhASBallZSY06XmCzQa/72OU3+gdyKBwBJpK5KzajAkvb4GngSLEhkanNXVHhNQk1JbHFiUzlPR3pqRnh3bDd6eVNRMkt5SzJGVW1XL3UrWlU9Gjdpby5wcm92ZW5hbmNlLnByb3RvLmxvYW4uTG9hblByb3RvcyRUcmlNZXJnZVJlcG9ydHNMaXN0Ig90cmlNZXJnZVJlcG9ydHMifAoRdHJpX21lcmdlX3JlcG9ydHMaLDQ3REVRcGo4SEJTYSsvVEltVys1SkNldVFlUmttNU5NcEpXWkczaFN1RlU9Ijdpby5wcm92ZW5hbmNlLnByb3RvLmxvYW4uTG9hblByb3RvcyRUcmlNZXJnZVJlcG9ydHNMaXN0KAEqMAosNDdERVFwajhIQlNhKy9USW1XKzVKQ2V1UWVSa201Tk1wSldaRzNoU3VGVT0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQAQr7AwotL3Byb3ZlbmFuY2UubWV0YWRhdGEudjEuTXNnV3JpdGVSZWNvcmRSZXF1ZXN0EskDCuwCChN1bmRlcndyaXRpbmdfcGFja2V0EiEBIFqWVlJjTpeYLNBr/vY5Tf6B3IoHAEmkrkrNqMCS9vgafRIsSGRqc1dUeE1CTUlscWJTOU9HempGeHdsN3p5U1EyS3lLMkZVbVcvdStaVT0aOWlvLnByb3ZlbmFuY2UucHJvdG8uVW5kZXJ3cml0aW5nUHJvdG9zJFVuZGVyd3JpdGluZ1BhY2tldCISdW5kZXJ3cml0aW5nUGFja2V0IoABChN1bmRlcndyaXRpbmdfcGFja2V0GixLeXkrZXdzbVVuc2ZOQjkvb2RLQTBCSmZtQWRUZjh5Nit1MGdDbWd0KytvPSI5aW8ucHJvdmVuYW5jZS5wcm90by5VbmRlcndyaXRpbmdQcm90b3MkVW5kZXJ3cml0aW5nUGFja2V0KAEqMAosS3l5K2V3c21VbnNmTkI5L29kS0EwQkpmbUFkVGY4eTYrdTBnQ21ndCsrbz0QARIpcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQqLQopcGIxeHZkNGs5amc1aDBkNGRoenI0ejB0eHR3ZTlwNXp4ZjU4eGNteGQQARjs59gIEnAKUwpGCh8vY29zbW9zLmNyeXB0by5zZWNwMjU2azEuUHViS2V5EiMKIQMX0ge/k4HkKIz6lw6XCME+4EeIIRPTQuL2LHTTkYEa9xIECgIIARiDrc0CEhkKEwoFbmhhc2gSCjk4NDYyMzA2MjUQ/Ys/GkAY/oyGPbciMHJJHNdMTk2V+aIPiLKr4jAKaDuZQ9aWdwNHE+VbIUvshRicYQ5pLj3AL20pa09kshkdJQ9KtlND"
              },
              {
                "hash": "169159903A0482120C880441CF8E5B6FB7FF7CB0DF9ECA08208435D965F5C693",
                "height": "18232283",
                "index": 1,
                "tx_result": {
                  "code": 0,
                  "data": "EjIKKi9jb3Ntb3MuZ3JvdXAudjEuTXNnU3VibWl0UHJvcG9zYWxSZXNwb25zZRIECJvHCQ==",
                  "log": "",
                  "info": "",
                  "gas_wanted": "227026",
                  "gas_used": "171822",
                  "events": [
                    {
                      "type": "coin_spent",
                      "attributes": [
                        {
                          "key": "spender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "432484530nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_received",
                      "attributes": [
                        {
                          "key": "receiver",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "432484530nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "transfer",
                      "attributes": [
                        {
                          "key": "recipient",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "432484530nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "sender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "fee",
                          "value": "2162422650nhash",
                          "index": true
                        },
                        {
                          "key": "fee_payer",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "min_fee_charged",
                          "value": "432484530nhash",
                          "index": true
                        },
                        {
                          "key": "fee_payer",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "acc_seq",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh/98",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "tx",
                      "attributes": [
                        {
                          "key": "signature",
                          "value": "/WgijeeMtYexd66Ldqvjh1UmwWyRTgeF43Fs0L6gUb1M3v+cs46s8zsClBjerw0RbHmdb2L2IHGAyd8UHfjBww==",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "action",
                          "value": "/cosmos.group.v1.MsgSubmitProposal",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        },
                        {
                          "key": "module",
                          "value": "group",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "cosmos.group.v1.EventSubmitProposal",
                      "attributes": [
                        {
                          "key": "proposal_id",
                          "value": "\"156571\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "cosmos.group.v1.EventVote",
                      "attributes": [
                        {
                          "key": "proposal_id",
                          "value": "\"156571\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_spent",
                      "attributes": [
                        {
                          "key": "spender",
                          "value": "pb1td8ycyhc2txl27zj9ln6kmj4paaulhjh535al0sv3r9vf6c0wc7s7xggmk",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "6028000000nhash",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_received",
                      "attributes": [
                        {
                          "key": "receiver",
                          "value": "pb18c9hd74gl4et35t8xd999yfh4nh7c2h8x5l5jj",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "6028000000nhash",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "transfer",
                      "attributes": [
                        {
                          "key": "recipient",
                          "value": "pb18c9hd74gl4et35t8xd999yfh4nh7c2h8x5l5jj",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1td8ycyhc2txl27zj9ln6kmj4paaulhjh535al0sv3r9vf6c0wc7s7xggmk",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "6028000000nhash",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "sender",
                          "value": "pb1td8ycyhc2txl27zj9ln6kmj4paaulhjh535al0sv3r9vf6c0wc7s7xggmk",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "cosmos.group.v1.EventProposalPruned",
                      "attributes": [
                        {
                          "key": "proposal_id",
                          "value": "\"156571\"",
                          "index": true
                        },
                        {
                          "key": "status",
                          "value": "\"PROPOSAL_STATUS_ACCEPTED\"",
                          "index": true
                        },
                        {
                          "key": "tally_result",
                          "value": "{\"yes_count\":\"1\",\"abstain_count\":\"0\",\"no_count\":\"0\",\"no_with_veto_count\":\"0\"}",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "cosmos.group.v1.EventExec",
                      "attributes": [
                        {
                          "key": "logs",
                          "value": "\"\"",
                          "index": true
                        },
                        {
                          "key": "proposal_id",
                          "value": "\"156571\"",
                          "index": true
                        },
                        {
                          "key": "result",
                          "value": "\"PROPOSAL_EXECUTOR_RESULT_SUCCESS\"",
                          "index": true
                        },
                        {
                          "key": "msg_index",
                          "value": "0",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_spent",
                      "attributes": [
                        {
                          "key": "spender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1729938120nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "coin_received",
                      "attributes": [
                        {
                          "key": "receiver",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1729938120nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "transfer",
                      "attributes": [
                        {
                          "key": "recipient",
                          "value": "pb17xpfvakm2amg962yls6f84z3kell8c5lehg9xp",
                          "index": true
                        },
                        {
                          "key": "sender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        },
                        {
                          "key": "amount",
                          "value": "1729938120nhash",
                          "index": true
                        }
                      ]
                    },
                    {
                      "type": "message",
                      "attributes": [
                        {
                          "key": "sender",
                          "value": "pb1znuc2vpg36vegm84mltsmhqn3z9p4sfncaemuh",
                          "index": true
                        }
                      ]
                    }
                  ],
                  "codespace": ""
                },
                "tx": "Ct4CCtsCCiIvY29zbW9zLmdyb3VwLnYxLk1zZ1N1Ym1pdFByb3Bvc2FsErQCCj1wYjF0ZDh5Y3loYzJ0eGwyN3pqOWxuNmttajRwYWF1bGhqaDUzNWFsMHN2M3I5dmY2YzB3YzdzN3hnZ21rEilwYjF6bnVjMnZwZzM2dmVnbTg0bWx0c21ocW4zejlwNHNmbmNhZW11aBokOTNmMjY5YjItMGViYi00MzRmLWExZDEtNzg4MjNjNTgzMGNlIp8BChwvY29zbW9zLmJhbmsudjFiZXRhMS5Nc2dTZW5kEn8KPXBiMXRkOHljeWhjMnR4bDI3emo5bG42a21qNHBhYXVsaGpoNTM1YWwwc3Yzcjl2ZjZjMHdjN3M3eGdnbWsSKXBiMThjOWhkNzRnbDRldDM1dDh4ZDk5OXlmaDRuaDdjMmg4eDVsNWpqGhMKBW5oYXNoEgo2MDI4MDAwMDAwKAESbQpQCkYKHy9jb3Ntb3MuY3J5cHRvLnNlY3AyNTZrMS5QdWJLZXkSIwohAo27/S5GCE98x6SrjCDZQ7vB3MsHbv4DRTVfxJmTfG1nEgQKAggBGGISGQoTCgVuaGFzaBIKMjE2MjQyMjY1MBDS7Q0aQP1oIo3njLWHsXeui3ar44dVJsFskU4HheNxbNC+oFG9TN7/nLOOrPM7ApQY3q8NEWx5nW9i9iBxgMnfFB34wcM="
              }
            ],
            "total_count": "2"
          }
    """.trimIndent()
}