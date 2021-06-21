package logic;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.springframework.beans.factory.annotation.Autowired;

import controllers.NotificationController;

import com.fasterxml.jackson.core.JsonProcessingException;

import entities.Agent;
import entities.Entity;
import entities.Floorplan;
import geo.Position;
import main.MqttPaho;
import main.Resources;
import pathfinding.AStarGrid;
import pathfinding.AStarNode;
import pathfinding.NodeState;
import entities.SensorActive;
import entities.SensorPassive;

public class Simulator {
	
	private LocalDateTime clock; // LocalDateTime.of(2020, 1, 1, 0, 0, 0, 0); 
	private boolean instantSimulation = true;
	private double relativeTime; // how many/few real-time seconds should a simulated second take
	
	private boolean mqttOutput;
	private int qualityOfService;
	private String mqttHost;
	private String mqttPort;
	private String rootTopic;
	
	private Long seed;

	private NotificationController notification;

	private Input input = Resources.getInput();
	private Floorplan floorplan = Resources.getFloorplan();
	private AStarGrid grid = Resources.getaStarGrid();
	private Agent agent = new Agent(Resources.getFloorplan().getAgent());
	
	ArrayList<SensorPassive> passiveSensors = floorplan.getPassiveSensors();
	ArrayList<SensorActive> activeSensors = floorplan.getActiveSensors();
	
	public Simulator(LocalDateTime clock, boolean instantSimulation, double relativeTime, boolean mqttOutput, int qualityOfService, String mqttHost, String mqttPort, String rootTopic, Long seed) {
		this.clock = clock;
		this.instantSimulation = instantSimulation;
		this.relativeTime = relativeTime;
		this.mqttOutput = mqttOutput;
		this.qualityOfService = qualityOfService;
		this.mqttHost = mqttHost;
		this.mqttPort = mqttPort;
		this.rootTopic = rootTopic;
		this.seed = seed;
	}
	
	

	public Simulator() throws MqttException { 
	}
	
	// next-event time progression discrete-event simulation
	public void startSimulator() throws InterruptedException, MqttPersistenceException, MqttException, JsonProcessingException {
		
		// reset sensors' lastTriggerTime variable
		for (SensorPassive sensor : passiveSensors) {
			sensor.setLastTriggerTime(null);
		}
		
		// start MQTT client if appropriate
		if (mqttOutput == true) {
			Resources.setMqtt(new MqttPaho(mqttHost, mqttPort, rootTopic, qualityOfService));
		} else {
			Resources.setMqtt(null);
		}
		
		// instantiate Random object with specified seed
		if (seed == null) {
			Resources.setRandom(new Random());
		} else {
			Resources.setRandom(new Random(seed));
		}
				
		String[] statementArray = input.getInputArray();
		Pattern gotoPattern = input.getGotopattern();
		Pattern interactPattern = input.getInteractpattern();
		Pattern waitPattern = input.getWaitpattern();
		Pattern entityPattern = input.getGotoentitypattern();
		
		System.out.println("*** Simulation has started ***"); //test
		notification.notifyToClient("*** Simulation has started ***");
		
		for (String statement : statementArray) {
			System.out.println("* "+statement+":"); //test
			notification.notifyToClient("* "+statement+":");
			
			// GOTO
			if (gotoPattern.matcher(statement).matches()) {
				Position gotoPosition = new Position(
					Integer.parseInt(gotoPattern.matcher(statement).replaceAll("$1")),
					Integer.parseInt(gotoPattern.matcher(statement).replaceAll("$2"))
				);
				gotoInstructions(gotoPosition, new ArrayList<Position>());
			
			// WAIT
			} else if (waitPattern.matcher(statement).matches()) {
				long waitTime = Long.parseLong(waitPattern.matcher(statement).replaceAll("$1")) * 1000000000; // converts seconds to nanoseconds
				waitInstructions(waitTime);
			
			// INTERACT
			} else if (interactPattern.matcher(statement).matches()) {
				String sensorName = interactPattern.matcher(statement).replaceAll("$1");
				String command = interactPattern.matcher(statement).replaceAll("$2");
				interactInstructions(sensorName, command);
			
			// GOTO ENTITY
			} else if (entityPattern.matcher(statement).matches()) {
				String entityName = entityPattern.matcher(statement).replaceAll("$1");
				gotoEntityInstructions(entityName);
			}
		}
		
		System.out.println("*** Simulation has ended ***"); //test
		notification.notifyToClient("*** Simulation has ended ***");
	}

	private void gotoInstructions(Position gotoPosition, ArrayList<Position> exemptedCollisions) throws InterruptedException, MqttPersistenceException, MqttException, JsonProcessingException {
		List<AStarNode> path;
		path = grid.getPath(
				agent.getPosition().getX(), 
				agent.getPosition().getY(), 
				gotoPosition.getX(), 
				gotoPosition.getY(),
				exemptedCollisions);
		
		// detects if goto is impossible (HALTING ERROR)
		if (path.isEmpty()) {
			System.out.println("ERROR: coordinates are not reachable");
			notification.notifyToClient("ERROR: coordinates are not reachable");
		}
		
		for (AStarNode node : path) {
			double distance = agent.getPosition().distance(new Position(node.getX(),node.getY())) * floorplan.getTileSideLength();
			long time = (long) ((distance / agent.getSpeed()) * 1000000000);
			
			//Agent jumps from start of tile to start of tile
			long halfTime = time/2;
			triggerPassiveSensors(halfTime);
			
			agent.setPosition(node.getX(), node.getY()); // moves agent
			System.out.println(clock.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnn")).toString()+" : "+agent.getPosition().toString()); // print time & position
			notification.notifyToClient(clock.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnn")).toString()+" : "+agent.getPosition().toString());
			triggerPassiveSensors(halfTime);
			
			
			/*
			//Agent jumps from middle of tile to middle of tile instead of start of tile to start of tile
			triggerPassiveSensors(time);
			agent.setPosition(node.getX(), node.getY()); // moves agent
			System.out.println(clock+" : "+agent.getPosition().toString()); // print time & position
			 */
			
		}
	}

	private void waitInstructions(long waitTime) throws InterruptedException, MqttPersistenceException, MqttException, JsonProcessingException {
		triggerPassiveSensors(waitTime);
		System.out.println(clock+" : "+agent.getPosition().toString()); // print time & position
		notification.notifyToClient(clock+" : "+agent.getPosition().toString());
	}
	
	private void interactInstructions(String sensorName, String command) throws MqttPersistenceException, InterruptedException, MqttException, JsonProcessingException {
		for (SensorActive activeSensor : activeSensors) {
			if (activeSensor.getName().equals(sensorName)) {
				if (!activeSensor.getInteractArea().contains(agent.getPosition())) {
					Position randomInteractPosition = activeSensor.getInteractArea().get(Resources.getRandom().nextInt(activeSensor.getInteractArea().size()));
					System.out.println("randomInteractPosition: "+randomInteractPosition); //test
					notification.notifyToClient("randomInteractPosition: "+randomInteractPosition);
					//intersection tiles of sensor's physicalArea tiles and interactArea tiles that should become walkable
					ArrayList<Position> intersectionArrayList = new ArrayList<Position>();
			        for (Position t : activeSensor.getPhysicalArea()) {
			            if(activeSensor.getInteractArea().contains(t)) {
			            	intersectionArrayList.add(t);
			            }
			        }
					
					gotoInstructions(randomInteractPosition, intersectionArrayList);
				}
				activeSensor.interact(command);
				break;
			}
		}
	}
	
	private void gotoEntityInstructions(String entityName) throws MqttPersistenceException, InterruptedException, MqttException, JsonProcessingException {
		ArrayList<Entity> union = new ArrayList<Entity>();
		union.addAll(floorplan.getEntities());
		union.addAll(activeSensors);
		
		for (Entity entity : union) {
			if (entity.getName().equals(entityName)) {
				if (!entity.getInteractArea().contains(agent.getPosition())) {
					ArrayList<Position> gotoAblePositions = new ArrayList<Position>();
					for (Position position : entity.getInteractArea()) {
						if (grid.getNode(position.getX(), position.getY()).getState() == NodeState.WALKABLE || entity.getPhysicalArea().contains(position)) {
							gotoAblePositions.add(position);
						}
					}
					Position randomInteractPosition = gotoAblePositions.get(Resources.getRandom().nextInt(gotoAblePositions.size()));
					System.out.println("randomInteractPosition: "+randomInteractPosition); //test
					notification.notifyToClient("randomInteractPosition: "+randomInteractPosition);
					//intersection tiles of entity's physicalArea tiles and interactArea tiles that should become walkable
					ArrayList<Position> intersectionArrayList = new ArrayList<Position>();
			        for (Position t : entity.getPhysicalArea()) {
			            if(entity.getInteractArea().contains(t)) {
			            	intersectionArrayList.add(t);
			            }
			        }
					
					gotoInstructions(randomInteractPosition, intersectionArrayList);
				}
				break;
			}
		}
	}
	
	private void updateTime(long nanos) throws InterruptedException {
		clock = clock.plusNanos(nanos);
		
		if (instantSimulation == false) {
			TimeUnit.NANOSECONDS.sleep( (long) Math.round(nanos * relativeTime));
		}
		
	}
	
	private void triggerPassiveSensors(long time) throws InterruptedException, MqttPersistenceException, MqttException, JsonProcessingException {
		LocalDateTime newTileTime = clock.plusNanos(time);
		ArrayList<TriggerEvent> eventList = new ArrayList<TriggerEvent>();
		for (SensorPassive sensor : passiveSensors) { 
			if (sensor.updateState() == true) {
				long i = 0;
				if (sensor.getLastTriggerTime() != null && sensor.getLastTriggerTime().until(clock,ChronoUnit.NANOS) < sensor.getTriggerFrequency()) {
					i = -sensor.getLastTriggerTime().until(clock,ChronoUnit.NANOS) + sensor.getTriggerFrequency();
				}
				for (; i < time; i = i + sensor.getTriggerFrequency()) {
					eventList.add(new TriggerEvent(sensor,clock.plusNanos(i)));
					sensor.setLastTriggerTime(clock.plusNanos(i));
				}
			}
		}
		eventList.sort(Comparator.comparing(TriggerEvent::getDateTime));
		for (TriggerEvent triggerEvent : eventList) {
			updateTime(clock.until(triggerEvent.getDateTime(),ChronoUnit.NANOS));
			((SensorPassive) triggerEvent.getSensor()).outputSensorReading();
		}
		updateTime(clock.until(newTileTime,ChronoUnit.NANOS));
	}
	
	/*
	private void triggerPassiveSensors(long time) throws InterruptedException, MqttPersistenceException, MqttException {
		LocalDateTime newTileTime = clock.plusNanos(time);
		ArrayList<TriggerEvent> eventList = new ArrayList<TriggerEvent>();
		for (SensorPassive sensor : grid.getNode(agent.getPosition().getX(), agent.getPosition().getY()).getPassiveTriggers()) { // for all passive sensors in the tile where the agent is present
			long i = 0;
			if (sensor.getLastTriggerTime() != null && sensor.getLastTriggerTime().until(clock,ChronoUnit.NANOS) < sensor.getTriggerFrequency()) {
				i = -sensor.getLastTriggerTime().until(clock,ChronoUnit.NANOS) + sensor.getTriggerFrequency();
			}
			for (; i < time; i = i + sensor.getTriggerFrequency()) {
				eventList.add(new TriggerEvent(sensor,clock.plusNanos(i)));
				sensor.setLastTriggerTime(clock.plusNanos(i));
			}
		}
		eventList.sort(Comparator.comparing(TriggerEvent::getDateTime));
		for (TriggerEvent triggerEvent : eventList) {
			updateTime(clock.until(triggerEvent.getDateTime(),ChronoUnit.NANOS));
			((SensorPassive) triggerEvent.getSensor()).trigger();
		}
		updateTime(clock.until(newTileTime,ChronoUnit.NANOS));
	}
	*/
	
	public String test() {
		//--- relativeTime ---//
		// test that relativeTime is not zero and that it has been instantiated
		if (relativeTime == 0) {
			return "ERROR: \"relativeTime\" has not been defined or has its value defined as zero";
		}
		// test that relativeTime is not negative
		if (relativeTime < 0) {
			return "ERROR: \"relativeTime\" has been defined as a negative integer";
		}
		
		//--- qualityOfService ---//
		if (qualityOfService < 0 || qualityOfService > 2) {
			return "ERROR: \"qualityOfService\" has not been defined as either 0, 1, or 2";
		}
		return "consumed";
	}
	
	//Accessors and Mutators
	public void setClock(LocalDateTime clock) {
		this.clock = clock;
	}

	public LocalDateTime getClock() {
		return clock;
	}

	public boolean getInstantSimulation() {
		return instantSimulation;
	}

	public void setInstantSimulation(boolean instantSimulation) {
		this.instantSimulation = instantSimulation;
	}

	public double getRelativeTime() {
		return relativeTime;
	}

	public void setRelativeTime(double timeFactor) {
		this.relativeTime = timeFactor;
	}

	public boolean getMqttOutput() {
		return mqttOutput;
	}

	public void setMqttOutput(boolean mqttOutput) {
		this.mqttOutput = mqttOutput;
	}

	public int getQualityOfService() {
		return qualityOfService;
	}

	public void setQualityOfService(int qualityOfService) {
		this.qualityOfService = qualityOfService;
	}

	public String getMqttHost() {
		return mqttHost;
	}

	public void setMqttHost(String mqttHost) {
		this.mqttHost = mqttHost;
	}

	public String getMqttPort() {
		return mqttPort;
	}

	public void setNotification(NotificationController notification){
		this.notification = notification;
	}

	public NotificationController getNotification() {
		return notification;
	}

	public void setMqttPort(String mqttPort) {
		this.mqttPort = mqttPort;
	}

	public String getRootTopic() {
		return rootTopic;
	}

	public void setRootTopic(String rootTopic) {
		this.rootTopic = rootTopic;
	}

	public Long getSeed() {
		return seed;
	}

	public void setSeed(Long seed) {
		this.seed = seed;
	}

	public Agent getAgent() {
		return agent;
	}
	
}

















// *****************************Old way of triggering passive sensors

/*
for (Sensor sensor : grid.getNode(agent.getPosition().getX(), agent.getPosition().getY()).getPassiveTriggers()) {
	int triggerAmount = (int) (time / triggerFrequency); // trigger amount in the time slice where agent moves from A to B
	if (time % triggerFrequency == 0) { // avoid edge-case where sensor in tile A is trigger, agent moves from A to B, and a sensor in tile B is triggered - all at the same time.
		triggerAmount--;
	}
	
	for (int i = 0; i <= triggerAmount; i++) {
		System.out.println(clock.plusNanos(i*triggerFrequency)+" : "+sensor.getName()+" has been triggered!");
	}
}

clock = clock.plusNanos(time); //updates clock
*/


// ****************************fixed-increment time progression discrete-event simulation


/*
// fixed-increment time progression discrete-event simulation
public void startSimulator() {
	String[] statementArray = input.getInputArray();
	String gotoPattern = input.getGotopattern();
	Agent agent = floorplan.getAgent();
	
	List<AStarNode> path;
	double speed = floorplan.getAgent().getSpeed();
	
	System.out.println("Simulator has started"); //test
	
	for (String statement : statementArray) {
		System.out.println(statement); //test
		if (statement.matches(gotoPattern)) {
			Position gotoPosition = new Position(
				Integer.parseInt(statement.replaceAll(gotoPattern, "$1")),
				Integer.parseInt(statement.replaceAll(gotoPattern, "$2"))
			);
			path = grid.getPath(
					agent.getPosition().getX(), 
					agent.getPosition().getY(), 
					gotoPosition.getX(), 
					gotoPosition.getY());
			
			for (AStarNode node : path) {
				double distance = agent.getPosition().distance(new Position(node.getX(),node.getY()));
				long time = (long) ((distance / speed) * 1000000000);
				
				long passedTime = 0; 
				//Simulation Logic-Loop
				while (passedTime<time) {
					long start = System.nanoTime();
					
					System.out.println(clock+" : "+agent.getPosition().toString());
					
					for (Sensor sensor : node.getPassiveTriggers()) {
						System.out.println(clock+" : "+sensor.getName()+" has been triggered!");
					}
					
					
					
					
					
					clock = clock.plusNanos(nsPerTick); //updates clock
					passedTime = passedTime + nsPerTick;
					
					try {
						TimeUnit.NANOSECONDS.sleep(start + nsPerTick - System.nanoTime());
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} //thread sleeps for real-time rendering
					
					//System.out.println(start + nsPerTick - System.nanoTime()); //computation time left per loop 
				}
				clock = clock.minusNanos(passedTime-time);
				agent.setPosition(node.getX(), node.getY());
			}
			
			
		}
		
		
	}
}
*/



/*
public void startSimulator() {

//Pre-simulation operations
Sensor testSensor = new Sensor("Light_1", new ArrayList<Position>(Arrays.asList(new Position(3,2))), new ArrayList<Position>(Arrays.asList(new Position(2,2))));

//Simulation Logic-Loop
while (tick<60) {
	tick++;
	long start = System.nanoTime();
	clock = clock.plusNanos(nsPerTick); //updates clock
	
	processInput();
	update();
	render();
	
	//TimeUnit.NANOSECONDS.sleep(start + nsPerTick - System.nanoTime()); //thread sleeps for real-time rendering
	System.out.println(start + nsPerTick - System.nanoTime()); //computation time left per loop 
	System.out.println(clock);
	testSensor.onInteraction();
	//System.out.print("E");
	
}

}

public void startRenderingSimulator() throws InterruptedException {
	//Pre-simulation operations
	Sensor testSensor = new Sensor("Light_1", new ArrayList<Position>(Arrays.asList(new Position(3,2))), new ArrayList<Position>(Arrays.asList(new Position(2,2))));
	
	while (tick<60) {
		long start = System.nanoTime();
		clock = clock.plusNanos(nsPerTick); //updates clock
		processInput();
		update();
		render();
		TimeUnit.NANOSECONDS.sleep(start + nsPerTick - System.nanoTime()); //thread sleeps for real-time rendering
		
		System.out.println(start + nsPerTick - System.nanoTime()); //computation time left per loop 
		System.out.println(clock);
		testSensor.onInteraction();
		System.out.print("E");
		
		tick++;
	}
}


	private void processInput() {
		
	}

	private void render() {
		// TODO Auto-generated method stub
		
	}

	private void update() {
		// TODO Auto-generated method stub
		
	}
*/