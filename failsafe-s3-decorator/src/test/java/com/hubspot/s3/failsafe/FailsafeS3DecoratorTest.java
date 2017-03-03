package com.hubspot.s3.failsafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.s3.AbstractAmazonS3;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;

import net.jodah.failsafe.CircuitBreakerOpenException;

public class FailsafeS3DecoratorTest {

  @Test
  public void succeedingClientWorks() throws InterruptedException {
    AmazonS3 s3 = FailsafeS3Decorator.decorate(new SucceedingS3Client());

    for (int i = 0; i < 100; i++ ) {
      assertThat(s3.getObjectMetadata("test-bucket", "test-key")).isNotNull();
      Thread.sleep(100);
    }
  }

  @Test
  public void itShortCircuitsFailingClientEventually() throws InterruptedException {
    AmazonS3 s3 = FailsafeS3Decorator.decorate(new FailingS3Client());

    for (int i = 0; i < 100; i++ ) {
      try {
        s3.getObjectMetadata("test-bucket", "test-key");
      } catch (AmazonServiceException e) {
        // expected
        assertThat(e.getStatusCode()).isEqualTo(500);
      } catch (CircuitBreakerOpenException e) {
        return;
      }

      Thread.sleep(100);
    }

    fail("Failsafe never short-circuited");
  }

  @Test
  public void itDoesntCount404AsFailure() throws InterruptedException {
    AmazonS3 s3 = FailsafeS3Decorator.decorate(new MissingS3Client());

    for (int i = 0; i < 100; i++ ) {
      try {
        s3.getObjectMetadata("test-bucket", "test-key");
      } catch (AmazonServiceException e) {
        // expected
        assertThat(e.getStatusCode()).isEqualTo(404);
      }

      Thread.sleep(100);
    }
  }

  private static class FailingS3Client extends AbstractAmazonS3 {

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key) throws AmazonServiceException {
      AmazonS3Exception exception = new AmazonS3Exception("Internal Error");
      exception.setStatusCode(500);
      exception.setErrorType(ErrorType.Service);
      throw exception;
    }
  }

  private static class MissingS3Client extends AbstractAmazonS3 {

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key) throws AmazonServiceException {
      AmazonS3Exception exception = new AmazonS3Exception("Not Found");
      exception.setStatusCode(404);
      exception.setErrorType(ErrorType.Client);
      throw exception;
    }
  }

  private static class SucceedingS3Client extends AbstractAmazonS3 {

    @Override
    public ObjectMetadata getObjectMetadata(String bucketName, String key) throws AmazonServiceException {
      return new ObjectMetadata();
    }
  }
}
