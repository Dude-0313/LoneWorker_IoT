#include <Wire.h>
#include <Ultrasonic.h>
#include "rgb_lcd.h"
#include <Dhcp.h>
#include <WiFiUdp.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>
#include <Dns.h>

#include <PubSubClient.h>

/************************Hardware Related Macros************************************/
#define         MQ_PIN                       (A0)     //define which analog input channel you are going to use
#define         RL_VALUE                     (5)     //define the load resistance on the board, in kilo ohms
#define         RO_CLEAN_AIR_FACTOR          (9.83)  //RO_CLEAR_AIR_FACTOR=(Sensor resistance in clean air)/RO,
                                                     //which is derived from the chart in datasheet
 
/***********************Software Related Macros************************************/
#define         CALIBARAION_SAMPLE_TIMES     (50)    //define how many samples you are going to take in the calibration phase
#define         CALIBRATION_SAMPLE_INTERVAL  (500)   //define the time interal(in milisecond) between each samples in the
                                                     //cablibration phase
#define         READ_SAMPLE_INTERVAL         (50)    //define how many samples you are going to take in normal operation
#define         READ_SAMPLE_TIMES            (5)     //define the time interal(in milisecond) between each samples in 
                                                     //normal operation
 
/**********************Application Related Macros**********************************/
#define         GAS_LPG                      (0)
#define         GAS_CO                       (1)
#define         GAS_SMOKE                    (2)
#define         GAS_THRESH                   (10) // 10 PPM

#define         TEMP_VALUE                   (3)
#define         TEMP_MAX                     (60)
#define         TEMP_MIN                     (10)
#define         TEMP_PIN                     (A3) // A3
#define         BVALUE                       (4275)               // B value of the thermistor
#define         R0                           (100000)            // R0 = 100k

#define         ALERT_NONE                   (0)
#define         ALERT_GAS                    (1)
#define         ALERT_TEMP                   (2)

#define         BUZZER_PIN                  (6) // D6
#define         US_PIN                      (4) // D4
#define         PROX_THRESH                 (2)

#define         BROKER_HOST                 "192.168.1.101"
#define         TOKEN_ALERT                 "loneworker/alert"
#define         TOKEN_GAS_LPG               "loneworker/gas/lpg"
#define         TOKEN_GAS_CO                "loneworker/gas/co"
#define         TOKEN_GAS_SMOKE             "loneworker/gas/smoke"
#define         TOKEN_TEMP                  "loneworker/temp"

#define         SUCCESS                     true
#define         FAILURE                     false

/*****************************Globals***********************************************/
float           LPGCurve[3]  =  {2.3,0.21,-0.47};   //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent"
                                                    //to the original curve. 
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.21), point2: (lg10000, -0.59) 
float           COCurve[3]  =  {2.3,0.72,-0.34};    //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent" 
                                                    //to the original curve.
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.72), point2: (lg10000,  0.15) 
float           SmokeCurve[3] ={2.3,0.53,-0.44};    //two points are taken from the curve. 
                                                    //with these two points, a line is formed which is "approximately equivalent" 
                                                    //to the original curve.
                                                    //data format:{ x, y, slope}; point1: (lg200, 0.53), point2: (lg10000,  -0.22)                                                     
float           Ro           =  10;                 //Ro is initialized to 10 kilo ohms

float GAS_LPG_Reading = 0.0;
float GAS_CO_Reading = 0.0 ;
float GAS_SMOKE_Reading =0.0;

float TEMP_Reading=0;

char ssid[] = "JioFi_1670";           
char pass[] = "abc123";   
int status = WL_IDLE_STATUS;
IPAddress server(74,125,115,105);  // Backend Server IP
WiFiClient client;


rgb_lcd LCD;
Ultrasonic US_Sensor(US_PIN);

PubSubClient mqttClient;


void setupWiFi()
{
  Serial.println("Attempting to connect to WPA network...");
  Serial.print("SSID: ");
  Serial.println(ssid);

  status = WiFi.begin(ssid, pass);
  if ( status != WL_CONNECTED) { 
    Serial.println("Couldn't get a wifi connection");
    while(true);
  } 
  else {
    Serial.println("Connected to wifi");
    Serial.println("\nStarting connection...");
    // if you get a connection, report back via serial:
    if (client.connect(server, 80)) {
      Serial.println("connected");
    }
  }  
}

void setupMQTT()
{
    Serial.print("Connecting to broker ...");
    mqttClient.setServer(BROKER_HOST, 1883);
    if (mqttClient.connect("LoneWorkerEdge")) {
        Serial.println("Success");
        return ;
    } 
    Serial.println("Failed");
    return;   
}

void setupLCD(){
    LCD.begin(16, 2);
    normalLCD();
}
void setup()
{
  setupLCD();
  Serial.begin(9600);                               //UART setup, baudrate = 9600bps
  //setupWiFi();
  Serial.print("Calibrating...\n");                
  Ro = MQCalibration(MQ_PIN);                       //Calibrating the sensor. Please make sure the sensor is in clean air 
                                                    //when you perform the calibration                    
  Serial.print("Calibration is done...\n"); 
  Serial.print("Ro=");
  Serial.print(Ro);
  Serial.print("kohm");
  Serial.print("\n");
 // setupMQTT();
}

int alert_type=ALERT_NONE;
bool isAlerting=false;

void loop()
{
/*  if(!mqttClient.connected())
  {
    setupMQTT();
  }
  else {
    delay(200);
    mqttClient.loop();   
  }
*/
  ReadSensors();
   if((alert_type=Edge_Analytics())!=ALERT_NONE){
      GenerateAlert(alert_type); 
      isAlerting=true;
   }
   else if(isAlerting)
   {
      GenerateAlert(ALERT_NONE);
      isAlerting=false;
   }
   Serial.print("LPG:"); 
   Serial.print(GAS_LPG_Reading );
   Serial.print( "ppm" );
   Serial.print("    ");   
   Serial.print("CO:"); 
   Serial.print(GAS_CO_Reading );
   Serial.print( "ppm" );
   Serial.print("    ");   
   Serial.print("SMOKE:"); 
   Serial.print(GAS_SMOKE_Reading );
   Serial.print( "ppm" );
   Serial.print("    ");   
   Serial.print("TEMP ");
   Serial.print(TEMP_Reading );
   Serial.print("    ");   
   Serial.print("\n");
  // publishData();
}

void ReadSensors(){
  GetGasReadings();
  GetTempReading();
}


void GetGasReadings()
{
   GAS_LPG_Reading = MQGetGasPercentage(MQRead(MQ_PIN)/Ro,GAS_LPG);
   GAS_CO_Reading = MQGetGasPercentage(MQRead(MQ_PIN)/Ro,GAS_CO);
   GAS_SMOKE_Reading = MQGetGasPercentage(MQRead(MQ_PIN)/Ro,GAS_SMOKE);
}

void GetTempReading(){
  //TEMP_Reading = TempSensor.read11(TEMP_PIN);
    int a = analogRead(TEMP_PIN);

    float R = 1023.0/a-1.0;
    R = R0*R;

    TEMP_Reading = 1.0/(log(R/R0)/BVALUE+1/298.15)-273.15; // convert to temperature via datasheet
}
 
void publishData(){
  char buf[20];
  sprintf(buf,"%f",GAS_LPG_Reading);
  mqttClient.publish(TOKEN_GAS_LPG,buf);  
  sprintf(buf,"%f",GAS_CO_Reading);
  mqttClient.publish(TOKEN_GAS_CO,buf);  
  sprintf(buf,"%f",GAS_SMOKE_Reading);
  mqttClient.publish(TOKEN_GAS_SMOKE,buf);  
  sprintf(buf,"%f",TEMP_Reading);
  mqttClient.publish(TOKEN_TEMP,buf);  
  
}


float MQResistanceCalculation(int raw_adc)
{
  return ( ((float)RL_VALUE*(1023-raw_adc)/raw_adc));
}
 

float MQCalibration(int mq_pin)
{
  int i;
  float val=0;
 
  for (i=0;i<CALIBARAION_SAMPLE_TIMES;i++) {            //take multiple samples
    val += MQResistanceCalculation(analogRead(mq_pin));
    delay(CALIBRATION_SAMPLE_INTERVAL);
  }
  val = val/CALIBARAION_SAMPLE_TIMES;                   //calculate the average value
 
  val = val/RO_CLEAN_AIR_FACTOR;                        //divided by RO_CLEAN_AIR_FACTOR yields the Ro 
                                                        //according to the chart in the datasheet 
 
  return val; 
}

float MQRead(int mq_pin)
{
  int i;
  float rs=0.0;
 
  for (i=0;i<READ_SAMPLE_TIMES;i++) {
    rs += MQResistanceCalculation(analogRead(mq_pin));
    delay(READ_SAMPLE_INTERVAL);
  }
 
  rs = rs/READ_SAMPLE_TIMES;
 
  return rs;  
}
 

int MQGetGasPercentage(float rs_ro_ratio, int gas_id)
{
  if ( gas_id == GAS_LPG ) {
     return MQGetPercentage(rs_ro_ratio,LPGCurve);
  } else if ( gas_id == GAS_CO ) {
     return MQGetPercentage(rs_ro_ratio,COCurve);
  } else if ( gas_id == GAS_SMOKE ) {
     return MQGetPercentage(rs_ro_ratio,SmokeCurve);
  }    
 
  return 0;
}
 

int  MQGetPercentage(float rs_ro_ratio, float *pcurve)
{
  return (pow(10,( ((log(rs_ro_ratio)-pcurve[1])/pcurve[2]) + pcurve[0])));
}

int Edge_Analytics()
{
  if((GAS_LPG_Reading > GAS_THRESH) ||  (GAS_CO_Reading > GAS_THRESH)|| (GAS_SMOKE_Reading > GAS_THRESH)) {
    return ALERT_GAS;
  }
  else if( (TEMP_Reading < TEMP_MIN) || (TEMP_Reading > TEMP_MAX)) {
    return ALERT_TEMP;  
  }
  return ALERT_NONE;
}

void GenerateAlert(int type) {
  switch(type){
    case ALERT_NONE :   normalLCD();
                        soundBuzzer(false);
                      return;
    case ALERT_GAS : alertLCD("GAS Alert!!");
                     break;
    case ALERT_TEMP : alertLCD("Temprature Alert!!");                      
                     break; 
  }
  if(getProximity() > PROX_THRESH) {
          soundBuzzer(true);
  }
  else{
    soundBuzzer(false);
  }
}

void write2lcd(int line,const char *msg){
  LCD.setCursor(0,line);
  LCD.print(msg);   
}

void alertLCD(const char*msg) {
  LCD.clear();
  LCD.setRGB(255,0,0);
  write2lcd(1,msg);
  LCD.blinkLED();
}

void normalLCD() {
  LCD.clear();
  LCD.setRGB(0,0,255);
  LCD.noBlinkLED();
}


float getProximity() {
  float rangeInMeters;
  rangeInMeters = US_Sensor.MeasureInCentimeters();
  Serial.print("Proximity :");
  Serial.println(rangeInMeters);
  return rangeInMeters;
}

void soundBuzzer(bool state) {
  if(state) {
    Serial.println("Sound Buzzer");
     // digitalWrite(BUZZER_PIN,1); 
  } else {
    //digitalWrite(BUZZER_PIN,0);
    Serial.println("UnSound Buzzer");
    
  }
 
}

