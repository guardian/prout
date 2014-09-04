
Hit a production url, get the build commit

Fetch latest from GitHub repo

What are the missing commits? Display how far behind production is

If the commits are old, notify the commiters/authors

Update merged pull-requests. Are they on Production or not?!



https://lagger.com/lag/guardian/membership?url=https://membership.theguardian.com&token=2342112321321312

would return

{
site: {
url: "https://membership.theguardian.com",
commit: "460027cfd103c92a13e630967faee737c818eafa"
},
repo: {
url: "https://github.com/guardian/membership",
branch: {
 name: "master",
 commit: "22222222c92a13e630967faee737c818eafa"
}
