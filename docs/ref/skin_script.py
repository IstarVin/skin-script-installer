"""
Mobile Legends Skin Script Installer
Installs custom skin scripts to an Android device via ADB.
"""

import os
import shutil
import subprocess
from os.path import join, normpath

# Constants
STORAGE_ANDROID_ROOT = "/storage/emulated/0"
ML_ASSETS_PATH = "Android/data/com.mobile.legends/files/dragon2017/assets"
ML_ASSETS_PATH_OS = normpath(ML_ASSETS_PATH)
SCRIPTS_PATH = "Skin Scripts"


def validate_and_fix_script_structure(script_path):
    """
    Validates and fixes the script directory structure.

    If the script doesn't have the proper Android directory structure but contains
    Art files, it creates the necessary directory structure and moves files accordingly.
    Uses os.walk to find the Art folder even in deeper subdirectories.

    Args:
        script_path: Path to the selected skin script directory
    """
    files = os.listdir(script_path)

    # Check if the script needs restructuring
    if "Android" in files:
        return

    # Use os.walk to find the Art folder recursively
    art_parent_path = None
    for root, folders, _ in os.walk(script_path):
        if "Art" in folders:
            art_parent_path = root
            break

    if art_parent_path is None:
        # No Art folder found, no restructuring needed
        return

    # Create the proper directory structure
    proper_script_path = join(script_path, ML_ASSETS_PATH_OS)
    os.makedirs(proper_script_path, exist_ok=True)

    # Move all items from the parent of Art folder to the new structure
    for item in os.listdir(art_parent_path):
        source_path = join(art_parent_path, item)
        destination_path = join(proper_script_path, item)

        os.rename(source_path, destination_path)

    root_path = art_parent_path.replace(script_path + "/", "").split("/")[0]

    shutil.rmtree(join(script_path, root_path))


def list_available_scripts(scripts_directory):
    """
    Lists all available skin scripts in the scripts directory.

    Args:
        scripts_directory: Path to the directory containing skin scripts

    Returns:
        List of script names
    """
    return os.listdir(scripts_directory)


def get_user_script_choice(available_scripts):
    """
    Prompts the user to select a script from the available options.

    Args:
        available_scripts: List of available script names

    Returns:
        Selected script name
    """
    # Display available scripts with numbered list
    print("Available skin scripts:")
    for index, script_name in enumerate(available_scripts, start=1):
        print(f"{index}. {script_name}")

    # Get user input
    while True:
        try:
            choice_input = input("\nChoose script to install (enter number): ")
            choice_index = int(choice_input) - 1

            if 0 <= choice_index < len(available_scripts):
                return available_scripts[choice_index]
            else:
                print(f"Please enter a number between 1 and {len(available_scripts)}")
        except (ValueError, IndexError):
            print("Invalid input. Please enter a valid number.")


def push_files_to_device(script_path):
    """
    Pushes all files from the script directory to the Android device via ADB.

    Args:
        script_path: Path to the skin script directory
    """
    print("\nPushing files to device...")

    # Push the entire script directory and its contents to the Android device
    source_dir = join(script_path, "Android")

    command = ["adb", "push", source_dir, STORAGE_ANDROID_ROOT]

    print(" ".join(command))

    subprocess.run(command)

    print("\n\nInstallation complete!")


def main():
    """Main function to orchestrate the skin script installation process."""
    # Get list of available scripts
    available_scripts = list_available_scripts(SCRIPTS_PATH)

    if not available_scripts:
        print(f"No scripts found in '{SCRIPTS_PATH}' directory.")
        return

    # Let user choose a script
    selected_script = get_user_script_choice(available_scripts)
    print(f"\nSelected: {selected_script}")

    # Validate and prepare the script directory structure
    selected_script_path = join(SCRIPTS_PATH, selected_script)
    validate_and_fix_script_structure(selected_script_path)

    # Push files to the Android device
    push_files_to_device(selected_script_path)


if __name__ == "__main__":
    main()
