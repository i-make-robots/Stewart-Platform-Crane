//------------------------------------------------------------------------------
// Inverse Stewart Platform robot
// dan@marginallycelver.com 2013-01-09
//------------------------------------------------------------------------------
// Copyright at end of file.
// please see http://www.marginallyclever.com/istewart for more information.



//------------------------------------------------------------------------------
// INCLUDES
//------------------------------------------------------------------------------
// Adafruit motor driver library
#include <AFMotorDrawbot.h>

// Saving config
#include <EEPROM.h>
#include <Arduino.h>  // for type definitions



//------------------------------------------------------------------------------
// CONSTANTS
//------------------------------------------------------------------------------
// Comment out this line to silence most serial output.
//#define VERBOSE         (1)

// which motor is on which pin?
#define M1_PIN          (1)
#define M2_PIN          (2)

// NEMA17 are 200 steps (1.8 degrees) per turn.  If a spool is 0.8 diameter
// then it is 2.5132741228718345 circumference, and
// 2.5132741228718345 / 200 = 0.0125663706 thread moved each step.
// NEMA17 are rated up to 3000RPM.  Adafruit can handle >1000RPM.
// These numbers directly affect the maximum velocity.
#define STEPS_PER_TURN  (200.0)

#define STEP_DELAY      (2500)

// *****************************************************************************
// *** Don't change the constants below unless you know what you're doing.   ***
// *****************************************************************************

// Serial communication bitrate
#define BAUD            (57600)
// Maximum length of serial input message.
#define MAX_BUF         (64)



//------------------------------------------------------------------------------
// VARIABLES
//------------------------------------------------------------------------------
// Initialize Adafruit stepper controller
static AF_Stepper m1((int)STEPS_PER_TURN, M2_PIN);
static AF_Stepper m2((int)STEPS_PER_TURN, M1_PIN);

// Serial comm reception
static char buffer[MAX_BUF];  // Serial buffer
static int sofar;             // Serial buffer progress


//------------------------------------------------------------------------------
// METHODS
//------------------------------------------------------------------------------


//------------------------------------------------------------------------------
static void processCommand() {
  char *ptr=buffer;
  while(*ptr && ptr<buffer+sofar) {
    switch(*ptr) {
    case 'A': m1.onestep(FORWARD );  delayMicroseconds(STEP_DELAY);  break;
    case 'B': m1.onestep(BACKWARD);  delayMicroseconds(STEP_DELAY);  break;
    case 'C': m2.onestep(FORWARD );  delayMicroseconds(STEP_DELAY);  break;
    case 'D': m2.onestep(BACKWARD);  delayMicroseconds(STEP_DELAY);  break;
    }
    ptr=strchr(ptr,' ')+1;
  }
}


//------------------------------------------------------------------------------
void test_motors() {
  int i;
  for(i=0;i<STEPS_PER_TURN;++i) {  m1.onestep(FORWARD  );  delayMicroseconds(STEP_DELAY);  }
  for(i=0;i<STEPS_PER_TURN;++i) {  m1.onestep(BACKWARD );  delayMicroseconds(STEP_DELAY);  }
  for(i=0;i<STEPS_PER_TURN;++i) {  m2.onestep(FORWARD  );  delayMicroseconds(STEP_DELAY);  }
  for(i=0;i<STEPS_PER_TURN;++i) {  m2.onestep(BACKWARD );  delayMicroseconds(STEP_DELAY);  }
}


//------------------------------------------------------------------------------
void setup() {
  // start communications
  Serial.begin(BAUD);
  Serial.println("HELLO WORLD! I AM STEWART PLATFORM #0");

  // initialize the read buffer
  sofar=0;
  
  //test_motors();

  // echo readiness
  Serial.print("> ");
}


//------------------------------------------------------------------------------
void loop() {
  // See: http://www.marginallyclever.com/2011/10/controlling-your-arduino-through-the-serial-monitor/
  // listen for serial commands
  while(Serial.available() > 0) {
    buffer[sofar++]=Serial.read();
    if(buffer[sofar-1]==';') break;  // in case there are multiple instructions
  }
 
  // if we hit a semi-colon, assume end of instruction.
  if(sofar>0 && buffer[sofar-1]==';') {
    // what if message fails/garbled?

    buffer[sofar]=0;
 
    // do something with the command
    processCommand();
 
    // reset the buffer
    sofar=0;
 
    // echo readiness
    Serial.print("> ");
  }
}



//------------------------------------------------------------------------------
// Copyright (C) 2013 Dan Royer (dan@marginallyclever.com)
// Permission is hereby granted, free of charge, to any person obtaining a 
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation 
// the rights to use, copy, modify, merge, publish, distribute, sublicense, 
// and/or sell copies of the Software, and to permit persons to whom the 
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//------------------------------------------------------------------------------

