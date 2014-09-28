Prout
=====

_"Has your pull request been deployed yet?"_

Tells you when your pull-requests are live - tells you when they're not, and should be.

Prout comes from the philosophy that:

    Developers are responsible for checking their changes on Production

This becomes more important, and _easier_ once you move to a Continuous Deployment
release process. Important, because now a developer can break the site simply by
hitting 'Merge' on a pull request - but also easier because with such a small delay
(ie less than 10 minutes) between merging the work and having it ready to view in a
Production setting, the developer is in a much better place to review their work;
it's still fresh in their mind.

While everyone on your team may agree with this philosophy, that 10 minute lag
between merge and deploy can be enough time for a developer like me to get distracted
("Look, shiny thing!" or, more realistically, "What's the next bit of work?") and
forget about promptly reviewing their changes on Production.

Prout simply notifies developers in their pull request that the code has been _seen_
in Production (a slightly stronger statement than simply saying it's been deployed).


Configuration
-------------

* .prout.json in the root of your project
* Add callbacks to prout - ie a GitHub webhook and ideally also a post-deploy hook
* Expose the commit id of your build in your deployed system

Run your own instance of Prout
------------------------------

[![Deploy](https://www.herokucdn.com/deploy/button.png)](https://heroku.com/deploy?template=https://github.com/guardian/prout)
