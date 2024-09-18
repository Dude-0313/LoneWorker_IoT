#########  Example Makefile for Modbus master and slave ###########
# Build Revision :  Revisoin 1.0
# Build Date :      26-10-2016
# Change Log :       
# Build By :        Srinivasan
# Revised By :      
###################################################################

SRC = LoneWorker_Edge.cpp Ultrasound.cpp
EXE = LWEdge
OBJ = Ultrasound.o LoneWorker_Edge.o

INCLUDES = -I /opt/poky-edison/1.7.3/sysroots/core2-32-poky-linux/usr/include
CFLAGS   = -g -Wall -Werror -Wno-write-strings
LDFLAGS  = -L$(SDKTARGETSYSROOT)/usr/lib -lmosquitto -lupm-grove -lmraa -lupm-i2clcd -lupm-buzzer
CXX 	 = /opt/poky-edison/1.7.3/sysroots/x86_64-pokysdk-linux/usr/bin/i586-poky-linux/i586-poky-linux-g++
CC 	 = /opt/poky-edison/1.7.3/sysroots/x86_64-pokysdk-linux/usr/bin/i586-poky-linux/i586-poky-linux-gcc
STRIP	 = /opt/poky-edison/1.7.3/sysroots/x86_64-pokysdk-linux/usr/bin/i586-poky-linux/i586-poky-linux-strip

$(EXE): $(SRC)
	 $(CXX) $(CFLAGS) $(SRC) $(INCLUDES) $(LDFLAGS) -o $(EXE)
	 $(STRIP) $(EXE)
