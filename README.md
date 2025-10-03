## Liferay PaaS and RabbitMQ with Objects, CX and custom services ##
- A full end to end proof of content to integrate RabbitMQ in Liferay PaaS (and Liferay SaaS)
- See for [Liferay PaaS and RabbitMQ with Objects, CX and custom services](https://learn.liferay.com/w/dxp/cloud/platform-services/using-a-custom-service) Blog Post for more details

## Detailed Setup Steps ##
- Setup Liferay PaaS secrets for RabbitMQ credentials
  - **rabbit-mq-default-user** used by RabbitMQ service, user is a full RabbitMQ administator, mapped in the rabbitmq service LCP.json file.
  - **rabbit-mq-default-pass** password for **rabbit-mq-default-user**, mapped in the rabbitmq service LCP.json file.
  - **rabbit-mq-liferay-user** used by rabbitmqpublish CX and rabbitmqlistener custom service, this RabbitMQ user has queue read & write access only, mapped in their LCP.json files.
  - **rabbit-mq-liferay-pass** password **rabbit-mq-liferay-user**, mapped in their LCP.json files.
  - The values you enter will be used by RabbitMQ and the custom service and client extension.
  - The secrets don't need to be manually mapped to individual services, the service LCP.json files use the @rabbit-mq-xxxx syntax which will take care of the mappings.

- Deploy the RabbitMQ custom service and configure RabbitMQ
  - The rabbitmq/LCP.json in the repository is pre-configured, the memory and cpu can be reduced e.g. to 2048 and 1 respectively for demo purposes if Liferay PaaS resources are scarce.
  - Build and deploy the rabbitmq custom service in the Liferay PaaS environment.
    - It may take an hour or more for the RabbitMQ Management console to be accessible but the RabbitMQ shell is accessible once the service has started.
  - rabbitmqctl and rabbitmqadmin commands can be run from the RabbitMQ service shell.
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
    - rabbitmqctl set_permissions -p / ***\[rabbit-mq-liferay-user\]*** "" ".\*" ".\*"
        - Replace ***\[rabbit-mq-liferay-user\]*** and ***\[rabbit-mq-liferay-pass\]*** with the corresponding secret values.

- Create the Liferay Object
  - Create a Company scoped Liferay Object with Object Name 'RabbitTest' (plural RabbitTests) with the following fields and Publish it:
    - Mandatory Long Text field with field name 'input'
    - Optional Long Text field with field name 'output'
  - Class RabbitMQListener in rabbit-mq-listener-jar module references this in a number of places e.g. objectEntryDTORabbitTest and /o/c/rabbittests/

- Deploy the Object Action Client Extension
  - The rabbitmq/LCP.json in the repository is pre-configured using queue 'demo-queue'. Update the environment variable as needed.
  - For convenience the Object Action Client Extension creates 2 OAuths, one a User Agent used by the Client Extension, the other a Headless Server used by the rabbitmqlistener custom service.
  - The Object Name is referenced in the scopes for the OAuth Client Extensions in rabbit-mq-publish\client-extension.yaml.
  - Ensure the publisher and listener are using the same queue...
  - Build and deploy the rabbitmqpublish client extension in the Liferay PaaS environment.

- Setup Liferay PaaS secrets for rabbitmqlistener
  - Add the following secrets using the values from the OAuth 2.0 Administration > Rabbit MQ Listener OAuth Application Headless Server
    - **listener-oauth-client-id**
    - **listener-oauth-client-secret**
  - The secrets don't need to be manually mapped to individual services, the rabbitmqlistener service LCP.json files use the @listener-oauth-client-xxxx syntax which will take care of the mappings.

- Build and deploy rabbitmqlistener
  - Build the com.mw.rabbit.mq.listener.jar-1.0.0.jar jar file (from separate repository https://github.com/michael-wall/rabbit-mq-demo-listener)
  - Copy the jar file into the root of the rabbitmqlistener custom service folder.
  - The rabbitmqlistener/LCP.json in the repository is pre-configured using queue 'demo-queue', 'processed-queue' and 'error-queue'.
  - Ensure the publisher and listener are using the same queue...
  - Build and deploy the rabbitmqlistener custom service in the Liferay PaaS environment.

- Add the Object Action to the 'RabbitTest' Object
  - Trigger: On After Add
  - Action: object-action-executor[function#rabbit-mq-publish-object-action]
 
- Verify custom services status
  - Check the DXP Cloud Console to confirm the environment is setup:
    - The rabbitmq and rabbitmqlistener custom services are running.
    - The rabbitmqpublisher Client Extension custom service is running.

## Implementation Notes ##
- This is a ‘proof of concept’ that is being provided ‘as is’ without any support coverage or warranty.
- It was tested in Liferay PaaS with the Client Extension build pipeline feature enabled, using Liferay DXP QR 2025.Q1.14 with JDK 21 at compile time and runtime.
  - Ensure the DXP Cloud CI service is compiling with JDK 21 otherwise the Client Extension won't compile - see [Setting the JDK version](https://learn.liferay.com/w/dxp/cloud/platform-services/continuous-integration#setting-the-jdk-version).
- Check with your account manager if you wish to add custom services in Liferay PaaS:
  - See [Using a Custom Service](https://learn.liferay.com/w/dxp/cloud/platform-services/using-a-custom-service).
  - Ensure you have sufficient resources (memory, CPU and instances) check the 'Plan and Usage' screen in Liferay PaaS to see available resources.
  - The memory and cpu assigned to the custom services / client extension are fairly arbitrary, the rabbitmq memory and cpu can be reduced e.g. to memory 2048 and cpu 1 if resources are scarce.
- RabbitMQ setup notes:
  - The RabbitMQ queues can be created programatically e.g. the first time they are accessed but the manual setup is included to give better visibility of the implementation.
  - The RabbitMQ default credentials can be used by the publish and listener but using a dedicated account with limited permissions for the queue actions is more secure.
  - In a full system integration implementation where the publish and listener components are in seperate systems, they should each have their own credentials.
  - In the rabbitmq/LCP.json, port 5672 is configured to be internal whereas port 15672 is configured to be external:
    - **Make port 5672 public if the publisher or listener isn't on the Liferay PaaS environment private network.**
- Service dependencies
  - The POC doesn't define LCP.json dependencies between the custom or out of the box services.
  - If external systems are dependent on the RabbitMQ custom service, consider not including it in the regular build with the other services, and using the LCP CLI tool to manage build and deployment of the RabbitMQ service independent of a 'regular build'.
  - The RabbitMQ service uses the default 'RollingUpdate' deployment strategy which should avoid downtime during build deployments. See [Understanding Deployment Strategies](https://learn.liferay.com/w/dxp/cloud/updating-services-in-liferay-paas/understanding-deployment-strategies) for more details.
- For the POC RabbitMQ is unclustered:
  - A RabbitMQ cluster is recommended to avoid RabbitMQ being a single point of failure.
  - A RabbitMQ cluster requires additional setup. Increasing the service scale will not result in a clustered RabbitMQ environment.
  - Configuring RabbitMQ clustering is not in scope for this POC.
