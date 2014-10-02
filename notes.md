
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



What happens we use .prout.json in subdirectories?

We have to identify all prout configs. When we do a scan, we need to
hit all checkpoint urls.

If a pull request was merged that affected files under the .prout.json
directory,

If a PR is merged, what checkpoints does it NEED to be visible on?
The PR will have affected files under certain directories -
only checkpoints from .prout.json files under those directories
need be considered.
Just the ones under the folder that the checkpoint is defined in?

