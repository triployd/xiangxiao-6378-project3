import os
import sys
config = sys.argv[1]
net_id = sys.argv[2]
with open(config) as f:
	for line in f:
		if "dc" in line:
			if "#" in line and (line.find("#") < line.find("dc")):
				continue;
			index = line.find("dc")
			host = line[index : index + 4]
			os.system("ssh "+net_id+"@"+host+" "+"killall -u "+net_id)
			