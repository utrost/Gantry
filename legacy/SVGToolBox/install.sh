#!/bin/bash

# Colors
GREEN='\033[0;32m'
NC='\033[0m'

echo -e "${GREEN}Building SVGToolBox...${NC}"
mvn clean package -DskipTests

JAR_PATH="$(pwd)/target/svgtoolbox-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Build failed. JAR not found."
    exit 1
fi

echo -e "${GREEN}Creating wrapper script...${NC}"

# Create a wrapper script in /usr/local/bin (requires sudo usually)
# Or, simpler: Create an alias script in the user's bin if it exists, or just output the alias command.

INSTALL_DIR="$HOME/bin"
mkdir -p "$INSTALL_DIR"
SCRIPT_PATH="$INSTALL_DIR/svgtoolbox"

cat <<EOF > "$SCRIPT_PATH"
#!/bin/bash
java -jar "$JAR_PATH" "\$@"
EOF

chmod +x "$SCRIPT_PATH"

echo -e "${GREEN}Installed to $SCRIPT_PATH${NC}"
echo "Ensure $HOME/bin is in your \$PATH."
echo "You can now run: svgtoolbox -i input.svg -o out.svg ..."