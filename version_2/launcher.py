import os
import sys
config = sys.argv[1]
net_id = sys.argv[2]
path = os.getcwd();
with open(config) as f:
	count = 0
	for line in f:
		if "dc" in line:
			if "#" in line and (line.find("#") < line.find("dc")):
				continue;
			index = line.find("dc")
			host = line[index : index + 4]
			os.system("ssh -l "+'"'+net_id+'"'+" "+'"'+host+'"'+" "+ '"'+"cd "+path+";"+" java Project3 "+str(count)+" "+config+'"'+" &")
			count += 1
			