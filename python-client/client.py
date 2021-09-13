# -*- coding: utf-8 -*-
"""
Spyder Editor

This is a temporary script file.
"""

import requests
import json
print("Proof for the independence of the backend.\n")
response = requests.get("http://localhost:8080/api/system/ping")

print(response.text)

f = open('floorplan.json',)
floorplan = json.load(f)
f.close()

url1 = "http://localhost:8080/api/roomConfig/floorplan"

floorplanreq = requests.post(url1, json = floorplan)
print("\nsending floorplan: ")
print(floorplanreq.text)

f = open('input.json',)
inputFile = json.load(f)
f.close()

url2 = "http://localhost:8080/api/simulation/input"

inputreq = requests.post(url2, json = inputFile)
print("\nsending inputs: ")
print(inputreq.text)


f = open('simulator.json',)
simulator = json.load(f)
f.close()

url3 = "http://localhost:8080/api/simulation/simulator"

simulatorreq = requests.post(url3, json = simulator)
print("\nsending simulator info: ")
print(simulatorreq.text)


