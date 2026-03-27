Help me plan the architecture of an application that will change how software developers organise their work and work together as a team.

In a team, developers use a ticketing system (e.g. JIRA) with a workflow to the like of tickets gets created an put in a backlog, then are prioritized, get planned and then developed by developers, the work gets reviewed, QA'ed, then merged into master/main.

I want to create an application that will use AI/Code Assist to help with the planning, development and parts of the QA steps of this work. The application will integrate itself to get the tasks from the ticketing application. The developer would have a squadron of agents at their disposal. When moving a prioritized task to the "planning" stage, the code would get checkout out in a containerized/isolated environment, then an agent would run on the code using the ticket description as a prompt, and would offer a plan to the developer. The developer will be able to interact with the agent to refine the plan until they are satisfied. Then, the developer can move the task to a "propose code" stage. At that point, the agent would create code to implement the plan, and then move the task to "review". The review would combine a configurable set of at least one human reviewer (can be the developer, and possibly another person), and also another model can provide a review. Then, the task can be moved to "QA", were the model would perform an analysis of the task to make sure apporpriate unit, integration, e2e tests are in place, and provide a report, to make sure to get 100% line coverage with the tests. The developer, or a QA resource, can also do QA manually at this stage. Then, it can be moved to a "merge" step where it is merged in the appropriate branch depending on the gitflow of the team.

The application must migrate the status of and update the tickets and through the REST API of the ticketing platforms. For JIRA, support both the Cloud and Server (Data Centre) versions of the REST API.

This application is meant to support multiple developers working together as a team, sharing or having each a squadron of agents. The application must support multiple ticketing platforms, running in docker or kubernetes, on-prem in an airgap or using public cloud. The application must be scalable to support multiple thousands of developers in the same organisations working together on it. Multiple agents of multiple providers must be supported, including using Claude Code models through Github, and also Cohere models (e.g. Command-4) self-hosted.

The application must be extensively configurable to allow teams to configure the squadrons of the team or of individual developers to meet their means for availability of agents and for development workflows, among other settings.

Propose a thorough plan to implement this application.
Prioritize using existing projects/packages over reinventing the wheel.
Prioritize Open Source technologies to avoid paying license fees for packages/software.
If you need to use a microservice architecture, I want to use a Springboot backend.
Use Angular for any frontend.
Use the most recent versions of any dependencies.
The application must use https and other certificate-based security, and delegate the identity of the user properly when performing operations on their behalf on platforms.
