Need to clone and install this repo to run `npm run build:generate-sources` https://github.com/flatpak/flatpak-builder-tools/tree/master/node

# To build a new flatpack version
1. Update config.js to uncomment flatpak lines
2. update package.json version
3. Manually delete any /archives/build, /flatpak/.flatpak-builder, /flatpak/build_x folders.
4. NOTE: The output /archive/build image should be ~98 mb. It will grow if u dont do that
5. run `npm run package-flatpak`
6. this will create a zip file in the archives folder. Upload it to dropbox via `npm run upload-flatpak`
7. Switch to net.studio08.xbplay repo and update the com.studio08.xbplay repo with the new url and sha
8. Dont forget to run `build:generate-sources` if you added any new npm deps

Note: on steam deck flatpak-buillder is: org.flatpak.Builder

# To build steam binaries
1. Update config.js to uncomment steam lines and ensure package.json version is new.
2. Run `npm run dist-all`
3. Open the 'steam' repo
4. Update the build_all.bash script with the new version.
5. Run the build_all.bash script. DO NOT manually copy the files. This breaks symbolic links (on mac at least)

# To build raw binaries:
1. Update config.js to uncomment direct raw binaries lines and ensure package.json version is new.
2. Run `npm run dist-all`
3. Run `npm run upload-dist`
4. Go to dropbox (alexward1230@gmail.com) and copy the URL for all raw files and update the website/reddit links

# To update transations
- See here: https://github.com/alexwarddev1230/xbox-xcloud-player-fork/blob/main/README.md
- Its the same steps!
- I just run the npm command in this repo