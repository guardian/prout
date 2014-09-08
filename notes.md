
Hit a production url, get the build commit

Fetch latest from GitHub repo

What are the missing commits? Display how far behind production is

If the commits are old, notify the commiters/authors

Update merged pull-requests. Are they on Production or not?!

Authenticating update-requesting endpoints
------------------------------------------

Threats:

* Spam labels
* DOS, expending GitHub quota, hitting random websites for their commit ids

So, we should authenticate.

If an instance of pr-guardian is for a single org, is it reasonable to use shared credentials amongst all repos?
Leaving staff?

Shared credentials are not compartmentalised, if they are compromised you have to update all affected repos.

GitHub webhooks
---------------

For increased security, the shared secret would be different per repo (this gets us a little closer to offering
people from different orgs the ability to use the same instance of pr-guardian - but is this really a goal worth
striving for?).

Programatically setting-up webhooks is a permission restricted to admins:

https://developer.github.com/v3/repos/hooks/

Adding a team member (obviously) requires admin (basically org-ownership):
https://developer.github.com/v3/orgs/teams/#add-team-membership



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
