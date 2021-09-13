# Linac Backend

Linac is the result of the bachelor project of Erik Ravn Nikolajsen and Mamuna Azam, co-supervised by Gemma Di Federico and Andrea Burattin. The project is described in the document "*A platform to simulate agent interactions with IoT devices to facilitate process mining algorithm research*". Abstract of the thesis:

> *Solving the challenges that emerge from the integration of the internet of things and business process management require the research and development of novel techniques. The evaluation of these techniques necessitates access to simulated sensor data, that reflect known activities being performed by a human within various smart environments. This thesis describes the design process of an agent based smart home simulator, that allows for such data to be generated and published as a parsable MQTT stream. The simulator enables userdefinable environments via a formal markup language, sensor behaviour via an extensible backend, and activities via an application specific scripting language. The software agent was modeled to imitate human movement behaviour by the application of a suboptimal and nondeterministic pathfinding algorithm. Furthermore a webapplication was developed to act as a frontend for the simulation, which assists the user in defining the input, configuring the simulation, and viewing the output. The developed solution partially solves the aforementioned problem and paves the way for further research in the area by acting as a proof of concept.*

The complete report is availabe at: https://findit.dtu.dk/en/catalog/2691894192.

The documentation for the backend is available as [Swagger YAML file](https://editor.swagger.io/?url=https://raw.githubusercontent.com/DTU-SPE/linac-backend/main/RestAPI_Documentation.yaml). The API are currently hosted at DTU, and can be accessed at:
* `linac.compute.dtu.dk` using `http`
* `linac-backend-eu1.herokuapp.com` using `https`
* `linac-backend-eu2.herokuapp.com` using `https`

The service is offered with no guarantees.


