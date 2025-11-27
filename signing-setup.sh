#!/bin/bash
# Podium Signing Configuration Setup Script
# This script helps you set up the signing configuration using Git submodule

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     Podium Android Signing Configuration Setup          ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if signing submodule exists
if [ -d "signing" ] && [ -f "signing/.git" ]; then
    echo -e "${GREEN}✓ Signing submodule already exists${NC}"
    echo ""
    echo "Options:"
    echo "  1) Update signing configuration (git pull)"
    echo "  2) Remove and re-add submodule"
    echo "  3) Exit"
    echo ""
    read -p "Choose an option (1-3): " option
    
    case $option in
        1)
            echo -e "${BLUE}Updating signing configuration...${NC}"
            cd signing
            git pull origin main
            cd ..
            echo -e "${GREEN}✓ Signing configuration updated${NC}"
            ;;
        2)
            echo -e "${YELLOW}Removing existing submodule...${NC}"
            git submodule deinit -f signing
            git rm -f signing
            rm -rf .git/modules/signing
            echo -e "${GREEN}✓ Submodule removed${NC}"
            ;;
        3)
            exit 0
            ;;
        *)
            echo -e "${RED}✗ Invalid option${NC}"
            exit 1
            ;;
    esac
fi

# Add signing submodule
if [ ! -d "signing" ]; then
    echo ""
    echo -e "${YELLOW}To use Git submodule for signing configuration:${NC}"
    echo "1. Create a private GitHub repository (e.g., Podium-Signing)"
    echo "2. Add your keystore.properties and .jks file to it"
    echo "3. Enter the repository SSH URL below"
    echo ""
    echo -e "${BLUE}Enter your private signing repository SSH URL:${NC}"
    echo "  Example (SSH, recommended): git@github.com:YourUsername/Podium-Signing.git"
    echo ""
    echo -e "${YELLOW}Note: SSH authentication is recommended for private repositories${NC}"
    echo "      Make sure you have SSH keys set up with GitHub"
    echo ""
    read -p "Repository URL (or 'skip' to use local keystore.properties): " repo_url
    
    if [ "$repo_url" = "skip" ] || [ -z "$repo_url" ]; then
        echo ""
        echo -e "${YELLOW}Skipping submodule setup${NC}"
        echo -e "${BLUE}You can use local keystore.properties in project root${NC}"
        echo ""
        
        if [ ! -f "keystore.properties" ]; then
            echo "Creating keystore.properties from example..."
            cp keystore.properties.example keystore.properties
            echo -e "${GREEN}✓ Created keystore.properties${NC}"
            echo -e "${YELLOW}⚠ Please edit keystore.properties with your actual signing information${NC}"
        fi
        exit 0
    fi
    
    echo ""
    echo -e "${BLUE}Adding signing submodule...${NC}"
    git submodule add "$repo_url" signing
    git submodule update --init --recursive
    
    echo ""
    echo -e "${GREEN}✓ Signing submodule added successfully!${NC}"
    echo ""
fi

# Verify signing configuration
echo -e "${BLUE}Verifying signing configuration...${NC}"
if [ -f "signing/keystore.properties" ]; then
    echo -e "${GREEN}✓ Found: signing/keystore.properties${NC}"
    
    # Check for keystore file
    keystore_file=$(grep "storeFile=" signing/keystore.properties | cut -d= -f2)
    if [ -f "signing/$keystore_file" ]; then
        echo -e "${GREEN}✓ Found: signing/$keystore_file${NC}"
    else
        echo -e "${YELLOW}⚠ Warning: Keystore file not found: signing/$keystore_file${NC}"
    fi
elif [ -f "keystore.properties" ]; then
    echo -e "${GREEN}✓ Found: keystore.properties (project root)${NC}"
else
    echo -e "${YELLOW}⚠ No signing configuration found${NC}"
    echo "  Please add keystore.properties to signing/ or project root"
fi

echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                    Setup Complete!                       ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "Next steps:"
echo "  • Run: ./gradlew assembleRelease"
echo "  • Build configuration will automatically detect signing setup"
echo ""
echo "For more information, see: RELEASE_BUILD_GUIDE.md"
