package io.provenance.explorer.service

import io.provenance.explorer.Application
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.transaction.annotation.Transactional

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
open class ValidatorServiceTest{

    @Autowired
    lateinit var validatorService: ValidatorService


    @Test(expected=KotlinNullPointerException::class)
    @Ignore("TODO turn into integration tests")
    fun `should throw KotlinNullPointerException error when searching with invalid consensus address`() {
        validatorService.findAddressesByConsensusAddress("I'm not match worthy")
    }

    @Test(expected=KotlinNullPointerException::class)
    @Ignore("TODO turn into integration tests")
    fun `should throw KotlinNullPointerException error when searching with invalid consensus pub key address`() {
        validatorService.findAddressesByConsensusPubKeyAddress("I'm not match worthy either")
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `should return matching operator address from consesus address`(){
        val result = validatorService.findAddressesByConsensusAddress("tpvalcons1pkj6cy0a6acmm3q2augsgkj27h79k6ywqj35gu")
        Assert.assertEquals("tpvaloper14neg0whj7puhwks6536a8lqp7msvd9p028p7jm", result)
    }

    @Test
    @Ignore("TODO turn into integration tests")
    fun `should return matching operator address from consesus pubkey address`(){
        val result = validatorService.findAddressesByConsensusPubKeyAddress("tpvalconspub1zcjduepq2hs3lwp8d0u4j0389knqaer4j97quc8v9updrz4yecgkm3uy46ls6rm7ph")
        Assert.assertEquals("tpvaloper1y3n8quzwm480v3yqsjwezplrj2ts0s9wlyfwup", result)
    }
}