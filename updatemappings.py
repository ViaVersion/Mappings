# Automates the mapping thingy
# Bad at Bash + Bad at Python = awful hybrid code
import requests
import os
import shutil
import time

starttime = time.time()
d = "cd jars && "
forward = 0
backward = 0
rewind = 0
# os.system("java -jar MappingsGenerator-4.1.0.jar server.jar 1.21.4")
os.system("rm -rf jars/")
os.system("rm -rf RemappedJars/")
try:
    os.mkdir("RemappedJars")
    os.mkdir("jars")
except:
    print("idk what's wrong, ur thing trippin g")

def modrinthdl(item,name):
    get = requests.get(f"https://api.modrinth.com/v2/project/{item}/version").json()
    download = get[0]["files"][0]["url"]
    fileName = download.split("/")[-1]
    os.system(f"{d}wget -O {name}.jar '{download}'")

def unzip(name,directory):
    os.mkdir(f"jars/{directory}")
    os.system(f"{d}unzip {name} -d {directory}")

modrinthdl("viaversion","ViaVersionUnpatched")
modrinthdl("viabackwards","ViaBackwardsUnpatched")
modrinthdl("viarewind","ViaRewindUnpatched")
unzip("ViaVersionUnpatched.jar","ViaVersion")
unzip("ViaBackwardsUnpatched.jar","ViaBackwards")
unzip("ViaRewindUnpatched.jar","ViaRewind")

for file in os.listdir("output/"):
    if os.path.isfile(f"jars/ViaVersion/assets/viaversion/data/{file}"):
        forward += 1
        shutil.copy2(f"output/{file}",f"jars/ViaVersion/assets/viaversion/data/{file}")

for file in os.listdir("output/backwards/"):
    if os.path.isfile(f"jars/ViaBackwards/assets/viabackwards/data/{file}"):
        backward += 1
        shutil.copy2(f"output/backwards/{file}",f"jars/ViaBackwards/assets/viabackwards/data/{file}")

    if os.path.isfile(f"jars/ViaRewind/assets/viarewind/data/{file}"):
        rewind += 1
        
        shutil.copy2(f"output/backwards/{file}",f"jars/ViaRewind/assets/viarewind/data/{file}")

os.system(f"{d}cd ViaVersion && zip -r ../../RemappedJars/ViaVersion.jar *")
os.system(f"{d}cd ViaBackwards && zip -r ../../RemappedJars/ViaBackwards.jar *")
os.system(f"{d}cd ViaRewind && zip -r ../../RemappedJars/ViaRewind.jar *")
endtime = int((time.time() - starttime))
print(f"Updated {forward} Mappings for ViaVersion\nUpdated {backward} Mappings for ViaBackwards\nUpdated {rewind} Mappings for ViaRewind\nFinished in {endtime} Seconds")