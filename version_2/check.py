import os
import sys
config = sys.argv[1]
with open(config) as f:
	print("###.................checking outout files...............###")
	print("###if output files are incorrect it will point out where###")
	config1 = config.replace(".txt" , "")
	count = 0
	for line in f:
		if "dc" in line:
			if "#" in line and (line.find("#") < line.find("dc")):
				continue;
			count += 1
	for x in range(0, count-1):
		print("diff " + config1 + "-" + str(x) + ".out" + " " + config1 + "-" + str(x+1) + ".out")
		os.system("diff " + config1 + "-" + str(x) + ".out" + " " + config1 + "-" + str(x+1) + ".out")
