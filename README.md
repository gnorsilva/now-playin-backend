## Now playin

Now playin - showing you which artist are being played on twitter in real time

A project for fun to play around with Akka http streaming.

This is the backend server, it currently:

- Connects to twitter stream and filters on "now playing by, now playing -"
- Parses tweets to extract an artist's name
- Inserts the artist's name and played date to Mongo with a TTL of 31 days
- Exposes a Server Side Event streaming API endpoint for the most played artists in Json
