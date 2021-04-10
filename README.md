# AWS SQS Worker Daemon

A simple alternative to the Amazon SQS Daemon ("sqsd") used on AWS Beanstalk worker tier instances.


## Configuration
> *IMPORTANT:* In order for `x-sqsd` to work, you have to have configured the AWS Authentication Keys on you environment either as ENV VARS or using any of the other methods that AWS provides. For ways to do this, go [here](http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html).

#### Using Environment Variables

| **Property**                            | **Default**            | **Required**                       | **Description**                                                           |
|-----------------------------------------|------------------------|------------------------------------|---------------------------------------------------------------------------|
| `AWS_DEFAULT_REGION`                    | `us-east-1`            | no                                 | The region name of the AWS SQS queue.                                     |
| `AWS_ACCESS_KEY_ID`                     | -                      | yes                                | The access key to access the AWS SQS queue.                               |
| `AWS_SECRET_ACCESS_KEY`                 | -                      | yes                                | The secret key to access the AWS SQS queue.                               |
| `SQSD_QUEUE_URL`                        | -                      | yes                                | Your queue URL.                                                           |
| `SQSD_WORKER_CONCURRENCY`               | `10`                   | no                                 | Max number of messages process in parallel.                               |
| `SQSD_WAIT_TIME_SECONDS`                | `20` (max: `20`)       | no                                 | Long polling wait time when querying the queue.                           |
| `SQSD_WORKER_HTTP_URL`                  | _                      | yes                                | Your service endpoint/path where to POST the messages.                    |
| `SQSD_WORKER_HTTP_REQUEST_CONTENT_TYPE` | `application/json`     | no                                 | Message MIME Type.                                                        |
| `SQSD_WORKER_TIMEOUT`                   | `30000`                | no                                 | Max time that waiting for a worker response in milliseconds.              |
| `SQSD_WORKER_HEALTH_URL`                | _                      | yes                                | Your service endpoint/path for your service health.                       |
| `SQSD_WORKER_HEALTH_WAIT_TIME`          | `30`                   | no                                 | Time to between health checks.                                            |


## How to build
```
sbt compile
sbt universal:packageZipTarball
```

## How to build the docker image
```
docker build --tag x-sqsd:<some version> .
docker build --tag <some_company>/x-sqsd:<some version> .
```

## How to use

You should use the image created by yourself

````
docker -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e SQSD_QUEUE_URL=<queue-url> -e SQSD_WORKER_HEALTH_URL=<hearbeat-url> -e SQSD_WORKER_HTTP_URL=<worker-url> -it -d run some_image
````
