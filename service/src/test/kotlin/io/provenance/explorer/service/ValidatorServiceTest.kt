package io.provenance.explorer.service

import io.provenance.explorer.Application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.Transactional

@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [Application::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
open class ValidatorServiceTest{

    @Autowired
    lateinit var validatorService: ValidatorService


    @Test
    @Disabled("TODO turn into integration tests")
    fun `should throw KotlinNullPointerException error when searching with invalid consensus address`() {
        assertThrows<KotlinNullPointerException> { validatorService.findAddressByConsensus("I'm not match worthy") }
    }

    @Test
    @Disabled("TODO turn into integration tests")
    fun `should return matching operator address from consesus address`(){
        val result = validatorService.findAddressByConsensus("tpvalcons1pkj6cy0a6acmm3q2augsgkj27h79k6ywqj35gu")
        Assertions.assertEquals("tpvaloper14neg0whj7puhwks6536a8lqp7msvd9p028p7jm", result)
    }
}
