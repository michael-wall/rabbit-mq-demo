## Liferay PaaS and RabbitMQ with Objects, CX and custom services - a POC to integrate RabbitMQ in Liferay PaaS ##
- Scope is to integrate Liferay DXP with https://www.rabbitmq.com/.
- RabbitMQ runs as a custom service in the Liferay PaaS environment.
- An Object Action Client Extension publishes a message to a RabbitMQ queue.
- A 'remote' Spring Boot custom service listens for messages in the queue, processes them and updates the original Object Record.

## Repositories ##
The POC uses the following github repositories:
  - https://github.com/michael-wall/rabbit-mq-demo.git - this repository which contains the DXP Cloud Workspace rabbitmq custom service definition, the rabbitmqlistener custom service definition (excluding the compiled jar file) and the rabbit-mq-publish client extension.
  - https://github.com/michael-wall/rabbit-mq-demo-listener.git which contains the source code for the rabbitmqlistener jar file. The compiled jar file to be manually added to the rabbitmqlistener custom service.

## Setup ##
- Setup Liferay PaaS secrets for RabbitMQ credentials using appropriate values:
  - **rabbit-mq-default-user** used by RabbitMQ service, user is a full administrtor, mapped in the rabbitmq service LCP.json file.
  - **rabbit-mq-default-pass** password for **rabbit-mq-default-user**, mapped in the rabbitmq service LCP.json file.
  - **rabbit-mq-liferay-user** used by rabbitmqpublish CX and rabbitmqlistener custom service, user has queue read & write access only, mapped in their LCP.json files.
  - **rabbit-mq-liferay-pass** password **rabbit-mq-liferay-user**, mapped in their LCP.json files.
  - Note: These secrets don't need to be manually mapped to individual services, the service LCP.json files use the @rabbit-mq-xxxx syntax which will take care of the mappings.

- Deploy the RabbitMQ custom service and configure RabbitMQ
  - The rabbitmq/LCP.json in the repository is pre-configured. It is a StatefulSet service with a volume defined for /var/lib/rabbitmq to retain the RabbitMQ setup after a restart.
  - Build and deploy the rabbitmq custom service in the Liferay PaaS environment.
  - The RabbitMQ administration GUI can be accessed from the browser using HTTPS and port 15672 using the credentials from rabbit-mq-default-user and rabbit-mq-default-pass secrets.
  - The AMQP APIs use port 5672 to interact with the queues.
  - rabbitmqctl and rabbitmqadmin commands can be run from the RabbitMQ service shell.
  - Both ports are configured to be external ports.
    - Run these commands from the RabbitMQ service shell to create the required message queues:
      - rabbitmqadmin --username=***\[rabbit-mq-default-user\]*** --password=***\[rabbit-mq-default-pass\]*** declare queue name=demo-queue durable=true
      - rabbitmqadmin --username=***\[rabbit-mq-default-user\]*** --password=***\[rabbit-mq-default-pass\]*** declare queue name=processed-queue durable=true
      - rabbitmqadmin --username=***\[rabbit-mq-default-user\]*** --password=***\[rabbit-mq-default-pass\]*** declare queue name=error-queue durable=true
        - Durable ensures the queue (and it's contents) survive a RabbitMQ service restart.
        - Replace ***\[rabbit-mq-default-user\]*** and ***\[rabbit-mq-default-pass\]*** with the corresponding secret values.
  - Run this command from the RabbitMQ service shell to verify that the queue was created:
    - rabbitmqctl list_queues
  - Run these command from the RabbitMQ service shell to create the user that the publisher and listener components will use to connect to the queues:
    - rabbitmqctl add_user ***\[rabbit-mq-liferay-user\]*** ***\[rabbit-mq-liferay-pass\]***
    - rabbitmqctl set_permissions -p / ***\[rabbit-mq-liferay-user\]*** "" ".*" ".*"
        - Replace ***\[rabbit-mq-liferay-user\]*** and ***\[rabbit-mq-liferay-pass\]*** with the corresponding secret values.

- Create the Liferay Object
  - Create a Company scoped Liferay Object with Object Name 'RabbitTest' (plural RabbitTest) with the following fields and Publish it:
    - Mandatory Long Text field with field name 'input'
    - Optional Long Text field with field name 'output'
  - Class RabbitMQListener in rabbit-mq-listener-jar module references this in a number of places e.g. objectEntryDTORabbitTest and /o/c/rabbittests/

- Deploy the Object Action Client Extension
  - The rabbitmq/LCP.json in the repository is pre-configured using queue 'demo-queue'. Update the environment variable as needed.
  - For convenience the Object Action Client Extension creates 2 OAuths, one a User Agent used by the Client Extension, the other a Headless Server used by the rabbitmqlistener custom service.
  - The Object Name is referenced in the scopes for the OAuth Client Extensions in rabbit-mq-publish\client-extension.yaml.
  - Ensure the publisher and listener are using the same queue...
  - Build and deploy the rabbitmqpublish client extension in the Liferay PaaS environment.

- Build and deploy the rabbitmqlistener
  - Add the following secrets using the values from the OAuth 2.0 Administration > Rabbit MQ Listener OAuth Application Headless Server
    - **listener-oauth-client-id**
    - **listener-oauth-client-secret**
  - Note: These secrets don't need to be manually mapped to individual services, the service LCP.json files use the @listener-oauth-client-xxxx syntax which will take care of the mappings.
  - Build the com.mw.rabbit.mq.listener.jar-1.0.0.jar jar file (from separate repository https://github.com/michael-wall/rabbit-mq-demo-listener)
  - Copy the jar file into the root of the rabbitmqlistener custom service folder.
  - The rabbitmqlistener/LCP.json in the repository is pre-configured using queue 'demo-queue', 'processed-queue' and 'error-queue'.
  - Ensure the publisher and listener are using the same queue...
  - Build and deploy the rabbitmqlistener custom service in the Liferay PaaS environment.

- Add the Object Action to the 'RabbitTest' Object:
  - Trigger: On After Add
  - Action: object-action-executor[function#rabbit-mq-publish-object-action]

## Triggering the Integration ##
- Check the DXP Cloud Console to confirm the environment is setup:
  - The rabbitmq and rabbitmqlistener custom services are running
  - The rabbitmqpublisher Client Extension custom service is running.
- Create a Liferay Objects record using the 'RabbitTest' Object Definition, populating the 'input' field, leaving the 'output' field empty, and Save.
- This will trigger the Object Action to send a message to the 'demo-queue' queue using the rabbitmypublisher Client Extension Object Action.
- The Listener class in rabbitmqlistener will listen for the message and when it receives it, it will extract the 'id' and 'input' values and use these to update the 'output' value using the headless REST API PATCH endpoint and the Headless Server OAuth 2 profile.
- Wait 15 seconds (15 second sleep delay added for demo purposes) and refresh the Objects grid screen. The 'output' field of the Object Record should now be populated by the rabbitmqlistener custom service logic.
- The rabbitmqpublisher and rabbitmqlistener components have logging to show what is happening.
  - If the message is processed successfully in rabbitmqlistener then it is moved to the 'processed-queue'.
  - If the message is not processed successfully in rabbitmqlistener (e.g. due to an exception or missing JSON field or a non-200 response from the PATCH etc.) then it is moved to the 'error-queue'.
  - Run this command from the RabbitMQ service shell to see the queue message counts:
    - rabbitmqctl list_queues
  - Run this command from the RabbitMQ service shell to view the first message from the processed-queue (without 'consuming' it):
    - rabbitmqadmin --username=***\[rabbit-mq-default-user\]*** --password=***\[rabbit-mq-default-pass\]*** get queue=processed-queue count=1
      - Replace ***\[rabbit-mq-default-user\]*** and ***\[rabbit-mq-default-pass\]*** with the corresponding secret values.

## Notes ##
- This is a ‘proof of concept’ that is being provided ‘as is’ without any support coverage or warranty.
- It was tested in Liferay PaaS with the Client Extension build pipeline feature enabled, using Liferay DXP QR 2025.Q1.14 with JDK 21 at compile time and runtime.
  - Ensure the DXP Cloud CI service is compiling with JDK 21 otherwise the Client Extension won't compile - see https://learn.liferay.com/w/dxp/cloud/platform-services/continuous-integration#setting-the-jdk-version
- The rabbitmqlistener class has additional logging for troubleshooting and demonstration purposes only e.g. the OAuth Access Token is logged.
- The rabbitmqlistener is deployed as a Liferay PaaS custom service for convenience.
  - In a realworld scenario the listener would be outside of Liferay PaaS and built with another framework or technologies.
  - The use of a custom service shows that the listener can run completely outside of Liferay DXP, using OAuth 2 and the headless REST APIs to interact with Liferay DXP.
- The RabbitMQ queues can be created programatically e.g. the first time they are accessed, and a single set of credentials can be used but sharing a dedicated account with limited permissions for the queue actions is more secure plus the extra setup steps help give a better understanding of the implementation.
  - In a full implementation the publish and subscribe components would likely each use their own credentials.
- The RabbitMQ ports are intentionally public:
  - port 5672 allows access to the Rabbit MQ queues and requires credentials to perform any operations.
  - port 15672 allows access to the Rabbit MQ Administration GUI over HTTPS.
- For the POC RabbitMQ is unclustered:
  - A RabbitMQ cluster is recommended to avoid RabbitMQ being a single point of failure.
  - Configuring RabbitMQ clustering is not in scope for this POC.
